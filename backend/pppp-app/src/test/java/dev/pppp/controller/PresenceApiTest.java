package dev.pppp.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import dev.pppp.TestRedis;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedis.class)
class PresenceApiTest {
    @Autowired MockMvc mvc;

    @Test
    void openStreamsShowUpOncePerUser() throws Exception {
        for (var who : new String[] {"zoe@x.y", "abe@x.y", "zoe@x.y"}) {
            mvc.perform(get("/api/v1/events").with(user(who).roles("PLAYER"))).andExpect(request().asyncStarted());
        }
        mvc.perform(get("/api/v1/presence").with(user("abe@x.y").roles("PLAYER")))
                .andExpect(jsonPath("$[?(@.id == 'zoe@x.y')]", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == 'abe@x.y')].name").value("abe@x.y"));
    }
}
