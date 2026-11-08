package dev.pppp.controller;

import dev.pppp.exception.QueueFullException;
import dev.pppp.model.InputAccepted;
import dev.pppp.exception.RateLimitedException;
import dev.pppp.model.ChatMessage;
import dev.pppp.service.ChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.TestRedis;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.service.InputService;
import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedis.class)
class ChatApiTest {
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired SettingsService settings;
    @Autowired ChatService chat;
    @Autowired ObjectMapper json;
    @MockitoBean InputService inputs;
    @MockitoSpyBean EventPublisher events;

    @BeforeEach
    void clean() {
        redis.delete(redis.keys("chat:*"));
        redis.delete(redis.keys("ratelimit:*"));
        redis.delete("settings:input");
    }

    ResultActions send(String userId, String text) throws Exception {
        return mvc.perform(post("/api/v1/chat/messages").with(user(userId).roles("PLAYER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", text))));
    }

    @Test
    void sendThenPageHistoryNewestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("u" + i + "@y.z", "  hello " + i + " ").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.text").value("hello " + i))
                    .andExpect(jsonPath("$.user").value("u" + i + "@y.z"))
                    .andExpect(jsonPath("$.name").value("u" + i + "@y.z"))
                    .andExpect(jsonPath("$.kind").value("USER"))
                    .andExpect(jsonPath("$.command").value(false))
                    .andExpect(jsonPath("$.at").isNumber());
        }
        verify(events).publish(eq("CHAT_MESSAGE"), argThat(m -> ((ChatMessage) m).text().equals("hello 2")));

        String page1 = mvc.perform(get("/api/v1/chat/messages?limit=2").with(user("x@y.z").roles("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("hello 2"))
                .andExpect(jsonPath("$[1].text").value("hello 1"))
                .andReturn().getResponse().getContentAsString();
        String oldest = json.readTree(page1).get(1).get("id").asText();
        mvc.perform(get("/api/v1/chat/messages?before=" + oldest).with(user("x@y.z").roles("PLAYER")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("hello 0"));
        mvc.perform(get("/api/v1/chat/messages?limit=101").with(user("x@y.z").roles("PLAYER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lengthAndBlankLimits() throws Exception {
        send("a@y.z", "x".repeat(301)).andExpect(status().isBadRequest());
        send("b@y.z", "   ").andExpect(status().isBadRequest());
        send("c@y.z", "<b>" + "x".repeat(293) + "</b>").andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("<b>" + "x".repeat(293) + "</b>"));
    }

    @Test
    void onePerSecondPerUser() throws Exception {
        send("fast@y.z", "one").andExpect(status().isCreated());
        send("fast@y.z", "two").andExpect(status().isTooManyRequests());
        send("other@y.z", "three").andExpect(status().isCreated());
        Thread.sleep(1100);
        send("fast@y.z", "four").andExpect(status().isCreated());
    }

    @Test
    void mutedUserRejectedUntilExpiry() throws Exception {
        mvc.perform(post("/api/v1/admin/users/Loud@Y.Z/mute").with(user("p@y.z").roles("PLAYER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/Loud@Y.Z/mute").with(user("a@y.z").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":5}"))
                .andExpect(status().isNoContent());
        send("loud@y.z", "hi").andExpect(status().isForbidden());

        redis.opsForHash().put("chat:mutes", "loud@y.z", Long.toString(System.currentTimeMillis() - 1));
        send("loud@y.z", "hi").andExpect(status().isCreated());
        assertThat(redis.opsForHash().hasKey("chat:mutes", "loud@y.z")).isFalse();
    }

    @Test
    void adminDeletesPlayerCannot() throws Exception {
        String body = send("d@y.z", "oops").andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(delete("/api/v1/admin/chat/messages/" + id).with(user("p@y.z").roles("PLAYER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/chat/messages/" + id).with(user("a@y.z").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(events).publish("CHAT_DELETED", Map.of("id", id));
        assertThat(chat.history(null, 50)).isEmpty();
    }

    @Test
    void systemMessagesAreStoredAndNeverThrow() {
        chat.postSystem("Console powered on");
        chat.postSystem(null);
        assertThat(chat.history(null, 50)).singleElement()
                .satisfies(m -> assertThat(m.kind()).isEqualTo("SYSTEM"));
        verify(events).publish(eq("CHAT_MESSAGE"), any());
    }

    @Test
    void bridgeOffByDefault() throws Exception {
        send("e@y.z", "left2").andExpect(jsonPath("$.command").value(false));
        verify(inputs, never()).submit(anyString(), anyString(), anyInt());
    }

    @Test
    void bridgeOnSubmitsCommandLikeMessages() throws Exception {
        settings.save(new Settings.Input("ANARCHY", 10, 80, 50, 6, 6, 50, true));
        when(inputs.submit("f@y.z", "left2", 1)).thenReturn(new InputAccepted("1-0", "ANARCHY"));
        when(inputs.submit("g@y.z", "hello", 1)).thenThrow(new IllegalArgumentException("bad command"));
        when(inputs.submit("h@y.z", "a", 1)).thenThrow(new RateLimitedException("slow down"));
        when(inputs.submit("j@y.z", "b", 1)).thenThrow(new QueueFullException("full", 1));

        send("f@y.z", "left2").andExpect(status().isCreated()).andExpect(jsonPath("$.command").value(true));
        send("g@y.z", "hello").andExpect(status().isCreated()).andExpect(jsonPath("$.command").value(false));
        send("h@y.z", "a").andExpect(status().isCreated()).andExpect(jsonPath("$.command").value(false));
        send("j@y.z", "b").andExpect(status().isCreated()).andExpect(jsonPath("$.command").value(false));
        send("i@y.z", "hello there").andExpect(jsonPath("$.command").value(false));
        verify(inputs, never()).submit(eq("i@y.z"), anyString(), anyInt());
        assertThat(chat.history(null, 50)).anySatisfy(m -> assertThat(m.command()).isTrue());
    }
}
