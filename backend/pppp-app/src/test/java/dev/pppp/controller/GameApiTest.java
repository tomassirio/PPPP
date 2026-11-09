package dev.pppp.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.model.GameEvent;
import dev.pppp.repository.GameEventRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedis.class)
class GameApiTest {
    @Autowired MockMvc mvc;
    @Autowired GameEventRepository events;
    @Autowired StringRedisTemplate redis;

    @Test
    void historyIsNewestFirstWithDetails() throws Exception {
        redis.delete("game:events");
        events.append(GameEvent.of("BADGE_EARNED", "Boulder", Map.of("count", 1)));
        events.append(GameEvent.of("NEW_TOWN", "Cerulean", Map.of("town", "Cerulean City")));
        mvc.perform(get("/api/v1/game-events?limit=10").with(user("x@y.z").roles("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("NEW_TOWN"))
                .andExpect(jsonPath("$[1].details.count").value(1));
        mvc.perform(get("/api/v1/game-events?limit=0").with(user("x@y.z").roles("PLAYER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noStateWithoutACartridge() throws Exception {
        mvc.perform(get("/api/v1/game/state").with(user("x@y.z").roles("PLAYER"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/game/icons/25.png").with(user("x@y.z").roles("PLAYER"))).andExpect(status().isNotFound());
    }

    @Test
    void teaserIsPublicAndAnonymous() throws Exception {
        mvc.perform(get("/api/v1/public/teaser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").isNumber())
                .andExpect(jsonPath("$.party").isArray());
        mvc.perform(get("/api/v1/public/screen.png")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/game/icons/25.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/state")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/presence")).andExpect(status().isUnauthorized());
    }
}
