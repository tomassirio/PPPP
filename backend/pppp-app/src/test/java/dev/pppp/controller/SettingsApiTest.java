package dev.pppp.controller;

import dev.pppp.TestRedis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedis.class)
class SettingsApiTest {
    @Autowired MockMvc mvc;

    @Test
    void adminUpdatesRateLimitPlayerCannot() throws Exception {
        mvc.perform(put("/api/v1/admin/settings/rate-limit").with(csrf()).with(user("p").roles("PLAYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputsPerSecond\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/settings/rate-limit").with(csrf()).with(user("a").roles("PLAYER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputsPerSecond\":5}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/settings/rate-limit").with(user("a").roles("ADMIN")))
                .andExpect(jsonPath("$.inputsPerSecond").value(5));
        mvc.perform(put("/api/v1/admin/settings/rate-limit").with(csrf()).with(user("a").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputsPerSecond\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("inputsPerSecond must be greater than or equal to 1"));
        mvc.perform(put("/api/v1/admin/settings/autosave").with(csrf()).with(user("a").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"cron\":\"nope\",\"keep\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Quartz cron: nope"));
    }

    @Test
    void anonymousApiCallGets401() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
    }
}
