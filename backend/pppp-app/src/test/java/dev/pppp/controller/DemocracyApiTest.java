package dev.pppp.controller;

import dev.pppp.TestRedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.service.InputService;
import dev.pppp.service.ModeService;
import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class DemocracyApiTest {
    static final EmulatorHost.Status ON = new EmulatorHost.Status(EmulatorHost.Power.ON, "red", false, 1.0);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SettingsService settings;
    @Autowired InputService inputs;
    @Autowired ModeService modes;
    @Autowired StringRedisTemplate redis;
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
        redis.delete(List.of("players:active", "mode:votes"));
    }

    @Test
    void docExampleClosesEarlyOnMajorityAndRunsTheMedian() throws Exception {
        democracy(30);
        active("ash", "misty", "brock", "gary", "oak"); // oak never votes, so only the majority can close early

        String a = vote("gary", "a");
        await(() -> state().path("tally").path("tally").path("A").asInt() == 1);
        assertThat(state().path("tally").path("endsAt").asLong()).isGreaterThan(System.currentTimeMillis());
        String left2 = vote("ash", "left2");
        String left3 = vote("misty", "left3");
        String left = vote("brock", "left");

        awaitStatus(left, "EXECUTED");
        awaitStatus(left2, "EXECUTED");
        awaitStatus(left3, "EXECUTED");
        awaitStatus(a, "OUTVOTED");
        assertThat(presses).containsExactly(Button.LEFT, Button.LEFT);
        assertThat(state().path("tally").isNull()).isTrue();
    }

    @Test
    void windowClosesOnTimeout() throws Exception {
        democracy(1);
        active("u1", "u2", "u3", "u4", "u5");
        long start = System.currentTimeMillis();
        String b = vote("u1", "b");
        awaitStatus(b, "EXECUTED");
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(900);
        assertThat(presses).containsExactly(Button.B);
    }

    @Test
    void tieIsRandomAndSecondVoteIsOutvoted() throws Exception {
        democracy(1);
        active("u1", "u2", "u3", "u4", "u5");
        String b = vote("u1", "b");
        String a = vote("u2", "a");
        String again = vote("u1", "a"); // would make A win 2-1 if it counted
        awaitStatus(again, "OUTVOTED");
        await(() -> presses.size() == 1);
        boolean bWon = presses.getFirst() == Button.B;
        assertThat(presses.getFirst()).isIn(Button.A, Button.B);
        awaitStatus(bWon ? b : a, "EXECUTED");
        awaitStatus(bWon ? a : b, "OUTVOTED");
    }

    @Test
    void modeSwitchesAt80PercentAndBackAt50() throws Exception {
        active("p1", "p2", "p3", "p4", "p5");
        modeVote("p1", "DEMOCRACY");
        modeVote("p2", "DEMOCRACY");
        modeVote("p3", "DEMOCRACY").andExpect(jsonPath("$.DEMOCRACY").value(3));
        assertThat(modes.current()).isEqualTo("ANARCHY"); // 60%
        modeVote("p4", "DEMOCRACY");
        assertThat(modes.current()).isEqualTo("DEMOCRACY"); // 80%
        assertThat(state().path("modeVotes").path("DEMOCRACY").asInt()).isZero(); // cleared on switch

        modeVote("p1", "ANARCHY");
        modeVote("p2", "ANARCHY");
        assertThat(modes.current()).isEqualTo("DEMOCRACY"); // 40%
        modeVote("p3", "ANARCHY");
        assertThat(modes.current()).isEqualTo("ANARCHY"); // 60%
        modeVote("p3", "CHAOS").andExpect(status().isBadRequest());
    }

    @Test
    void sequencesRejectedInDemocracy() throws Exception {
        democracy(1);
        submit("seq", "{\"command\":\"left2down2\"}").andExpect(status().isBadRequest());
        String id = id(submit("rep", "{\"command\":\"left2\"}").andExpect(status().isAccepted()));
        awaitStatus(id, "EXECUTED");
        assertThat(presses).containsExactly(Button.LEFT, Button.LEFT);
    }

    private void democracy(int windowSeconds) {
        var d = Settings.Input.DEFAULT;
        settings.save(new Settings.Input("DEMOCRACY", windowSeconds, d.enterDemocracyPercent(), d.leaveDemocracyPercent(),
                d.holdFrames(), d.releaseFrames(), d.maxQueueLength(), d.chatCommands()));
    }

    private void active(String... users) {
        for (String u : users) modes.markActive(u + "@x.y");
    }

    private String vote(String user, String command) throws Exception {
        return id(submit(user, "{\"command\":\"" + command + "\"}").andExpect(status().isAccepted()));
    }

    private ResultActions modeVote(String user, String mode) throws Exception {
        return mvc.perform(post("/api/v1/votes/mode").with(user(user + "@x.y").roles("PLAYER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(202, 400));
    }

    private ResultActions submit(String user, String body) throws Exception {
        return mvc.perform(post("/api/v1/inputs").with(user(user + "@x.y").roles("PLAYER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode state() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/state").with(user("x@y.z").roles("PLAYER")))
                .andReturn().getResponse().getContentAsString());
    }

    private void awaitStatus(String id, String want) throws Exception {
        await(() -> want.equals(json.readTree(mvc.perform(get("/api/v1/inputs/" + id).with(user("x@y.z").roles("PLAYER")))
                .andReturn().getResponse().getContentAsString()).path("status").asText()));
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
