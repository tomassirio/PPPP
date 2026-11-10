package dev.pppp.security;

import dev.pppp.TestRedis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// Own context: spring-security-test's csrf() swaps the CSRF repository inside a shared cached context.
@SpringBootTest(properties = "spring.application.name=csrf-cookie-test")
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class CsrfCookieTest {
    @Autowired MockMvc mvc;

    @Test
    void firstRequestSetsCsrfCookieForTheSpa() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
