package dev.pppp.controller;

import dev.pppp.TestRedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.service.InputService;
import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedis.class)
class InputApiTest {
    static final EmulatorHost.Status ON = new EmulatorHost.Status(EmulatorHost.Power.ON, "red", false, 1.0);
    static final EmulatorHost.Status PAUSED = new EmulatorHost.Status(EmulatorHost.Power.ON, "red", true, 1.0);
    static final EmulatorHost.Status OFF = new EmulatorHost.Status(EmulatorHost.Power.OFF, "red", false, 1.0);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SettingsService settings;
    @Autowired InputService inputs;
    @MockitoBean EmulatorHost host;

    final List<Button> presses = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        settings.save(Settings.Input.DEFAULT);
        settings.save(Settings.RateLimit.DEFAULT);
        when(host.status()).thenReturn(ON);
        doAnswer(inv -> presses.add(inv.getArgument(0))).when(host).press(any(), anyInt(), anyInt());
        inputs.flush("test");
        await(() -> inputs.depth() == 0);
    }

    @Test
    void inputRunsToExecutedAndSequencesDoNotInterleave() throws Exception {
        String first = submit("ash@x.y", "{\"command\":\"left2down2\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("ANARCHY"))
                .andReturn().getResponse().getHeader("Location");
        String second = id(submit("misty@x.y", "{\"command\":\"a\",\"repeat\":3}").andExpect(status().isAccepted()));
        String third = id(submit("brock@x.y", "{\"command\":\"ABA\"}").andExpect(status().isAccepted()));

        assertThat(first).startsWith("/api/v1/inputs/");
        awaitStatus(third, "EXECUTED");
        mvc.perform(get(first).with(user("brock@x.y").roles("PLAYER")))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.executedAt").isNotEmpty());
        awaitStatus(second, "EXECUTED");

        assertThat(presses).containsExactly(
                Button.LEFT, Button.LEFT, Button.DOWN, Button.DOWN,
                Button.A, Button.A, Button.A,
                Button.A, Button.B, Button.A);
    }

    @Test
    void walkHoldsTheDirectionWithoutRelease() throws Exception {
        String walk = id(submit("red@x.y", "{\"command\":\"up\",\"walk\":true}").andExpect(status().isAccepted()));
        awaitStatus(walk, "EXECUTED");
        org.mockito.Mockito.verify(host).press(Button.UP, dev.pppp.service.InputScheduler.WALK_FRAMES, 0);
        submit("red2@x.y", "{\"command\":\"a\",\"walk\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitedGets429() throws Exception {
        settings.save(new Settings.RateLimit(1));
        submit("gary@x.y", "{\"command\":\"a\"}").andExpect(status().isAccepted());
        submit("gary@x.y", "{\"command\":\"a\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void badCommandGets400() throws Exception {
        submit("oak@x.y", "{\"command\":\"jump\"}").andExpect(status().isBadRequest());
        submit("oak2@x.y", "{\"command\":\"a6\"}").andExpect(status().isBadRequest());
    }

    @Test
    void fullQueueGets503ThenAdminFlushDropsEverything() throws Exception {
        var d = Settings.Input.DEFAULT;
        settings.save(new Settings.Input(d.mode(), d.democracyWindowSeconds(), d.enterDemocracyPercent(),
                d.leaveDemocracyPercent(), d.holdFrames(), d.releaseFrames(), 2, d.chatCommands()));
        when(host.status()).thenReturn(PAUSED);

        String held = id(submit("u1@x.y", "{\"command\":\"a\"}").andExpect(status().isAccepted()));
        await(() -> position(held) == 0); // scheduler picked it up and waits for resume
        String waiting = id(submit("u2@x.y", "{\"command\":\"b\"}").andExpect(status().isAccepted()));
        submit("u3@x.y", "{\"command\":\"a\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));

        assertThat(position(waiting)).isEqualTo(1);
        mvc.perform(get("/api/v1/queue").with(user("u1@x.y").roles("PLAYER")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user").value("u2@x.y"))
                .andExpect(jsonPath("$[0].command").value("B"));
        mvc.perform(get("/api/v1/state").with(user("u1@x.y").roles("PLAYER")))
                .andExpect(jsonPath("$.power").value("ON"))
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.mode").value("ANARCHY"))
                .andExpect(jsonPath("$.queueDepth").value(2))
                .andExpect(jsonPath("$.tally").isEmpty());

        mvc.perform(delete("/api/v1/admin/queue").with(user("u1@x.y").roles("PLAYER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/queue").with(user("boss@x.y").roles("PLAYER", "ADMIN")).with(csrf()))
                .andExpect(jsonPath("$.dropped").value(2));
        awaitStatus(held, "DROPPED");
        awaitStatus(waiting, "DROPPED");
        await(() -> inputs.depth() == 0);
        verify(host, never()).press(any(), anyInt(), anyInt());
    }

    @Test
    void consoleOffDropsInputs() throws Exception {
        when(host.status()).thenReturn(OFF);
        String id = id(submit("red@x.y", "{\"command\":\"start\"}").andExpect(status().isAccepted()));
        awaitStatus(id, "DROPPED");
        verify(host, never()).press(any(), anyInt(), anyInt());
    }

    @Test
    void unknownInputIs404() throws Exception {
        mvc.perform(get("/api/v1/inputs/1-0").with(user("x@y.z").roles("PLAYER"))).andExpect(status().isNotFound());
    }

    private ResultActions submit(String user, String body) throws Exception {
        return mvc.perform(post("/api/v1/inputs").with(user(user).roles("PLAYER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode statusOf(String idOrPath) throws Exception {
        String path = idOrPath.startsWith("/") ? idOrPath : "/api/v1/inputs/" + idOrPath;
        return json.readTree(mvc.perform(get(path).with(user("x@y.z").roles("PLAYER")))
                .andReturn().getResponse().getContentAsString());
    }

    private long position(String id) throws Exception {
        JsonNode p = statusOf(id).get("position");
        return p == null || p.isNull() ? -1 : p.asLong();
    }

    private void awaitStatus(String idOrPath, String want) throws Exception {
        await(() -> want.equals(statusOf(idOrPath).path("status").asText()));
    }

    interface Check { boolean ok() throws Exception; }

    private static void await(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!check.ok()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("condition not met within 5 s");
            Thread.sleep(20);
        }
    }
}
