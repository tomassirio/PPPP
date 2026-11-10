package dev.pppp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.service.ChatService;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;
import dev.pppp.service.ConsoleService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class AdminApiTest {
    static final Path DATA;
    static {
        try {
            DATA = Files.createTempDirectory("pppp-admin-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("pppp.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired SettingsService settings;
    @Autowired Scheduler scheduler;
    @Autowired ConsoleService consoleService;
    @MockitoBean EmulatorHost host;
    @MockitoBean ChatService chat;

    static final RequestPostProcessor ADMIN = user("a@x.y").roles("PLAYER", "ADMIN");
    static final RequestPostProcessor PLAYER = user("p@x.y").roles("PLAYER");

    @BeforeEach
    void off() {
        reset(host, chat);
        console(EmulatorHost.Power.OFF, null);
    }

    void console(EmulatorHost.Power power, String cartridgeId) {
        when(host.status()).thenReturn(new EmulatorHost.Status(power, cartridgeId, false, 1.0));
    }

    /** 32 KB with a valid header checksum. */
    static byte[] rom(String title, boolean cgb) {
        byte[] rom = new byte[32 * 1024];
        byte[] t = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, rom, 0x134, t.length);
        if (cgb) rom[0x143] = (byte) 0x80;
        int x = 0;
        for (int i = 0x134; i <= 0x14C; i++) x = x - rom[i] - 1;
        rom[0x14D] = (byte) x;
        return rom;
    }

    void upload(String filename, byte[] content, int expectedStatus) throws Exception {
        mvc.perform(multipart("/api/v1/admin/cartridges").file(new MockMultipartFile("file", filename, null, content))
                        .with(ADMIN).with(csrf()))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void uploadListDeleteRom() throws Exception {
        upload("../../evil/Pokemon Red!.GB", rom("POKEMON RED", false), 201);
        upload("crystal.gbc", rom("PM_CRYSTAL", true), 201);
        assertThat(DATA.resolve("roms/pokemon-red.gb")).exists();

        mvc.perform(get("/api/v1/admin/cartridges").with(ADMIN))
                .andExpect(jsonPath("$[?(@.id=='pokemon-red')].title").value("POKEMON RED"))
                .andExpect(jsonPath("$[?(@.id=='pokemon-red')].cgb").value(false))
                .andExpect(jsonPath("$[?(@.id=='crystal')].cgb").value(true))
                .andExpect(jsonPath("$[?(@.id=='crystal')].size").value(32 * 1024));

        upload("pokemon red.gb", rom("POKEMON RED", false), 409);
        byte[] broken = rom("X", false);
        broken[0x14D]++;
        upload("broken.gb", broken, 400);
        upload("notes.txt", rom("X", false), 400);
        upload("tiny.gb", new byte[0x150], 400);
        mvc.perform(get("/api/v1/admin/cartridges").with(PLAYER)).andExpect(status().isForbidden());

        console(EmulatorHost.Power.OFF, "crystal");
        mvc.perform(delete("/api/v1/admin/cartridges/crystal").with(ADMIN).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/cartridges/pokemon-red").with(ADMIN).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/cartridges/pokemon-red").with(ADMIN).with(csrf())).andExpect(status().isNotFound());
        assertThat(DATA.resolve("roms/pokemon-red.gb")).doesNotExist();
    }

    @Test
    void insertNeedsConsoleOffAndIsRemembered() throws Exception {
        upload("blue.gb", rom("POKEMON BLUE", false), 201);
        String body = "{\"cartridgeId\":\"blue\"}";

        console(EmulatorHost.Power.ON, "other");
        mvc.perform(put("/api/v1/admin/console/cartridge").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        console(EmulatorHost.Power.OFF, "blue");
        mvc.perform(put("/api/v1/admin/console/cartridge").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cartridgeId").value("blue"));
        verify(host).insert(eq("blue"), eq(DATA.resolve("roms/blue.gb")), eq(DATA.resolve("saves/blue.sav")));
        verify(chat).postAdmin(anyString());

        console(EmulatorHost.Power.ON, "blue");
        mvc.perform(put("/api/v1/admin/console/power").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"ON\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.power").value("ON"));
        verify(host).powerOn();
        assertThat(redis.<String, String>opsForHash().entries("console")).isEqualTo(Map.of("cartridgeId", "blue", "power", "ON"));

        mvc.perform(put("/api/v1/admin/console/speed").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"multiplier\":9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/console/reset").with(PLAYER).with(csrf())).andExpect(status().isForbidden());

        // A restart re-inserts and powers on.
        reset(host);
        console(EmulatorHost.Power.ON, "blue");
        consoleService.restore();
        verify(host).insert(eq("blue"), any(), any());
        verify(host).powerOn();
    }

    @Test
    void takeListLoadSaveStates() throws Exception {
        mvc.perform(post("/api/v1/admin/save-states").with(ADMIN).with(csrf())).andExpect(status().isConflict());

        console(EmulatorHost.Power.ON, "yellow");
        when(host.saveState()).thenReturn(new byte[] {1, 2, 3});
        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/admin/save-states").with(ADMIN).with(csrf()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.kind").value("manual"))
                        .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(DATA.resolve("states/yellow/" + id + ".state")).hasBinaryContent(new byte[] {1, 2, 3});

        mvc.perform(get("/api/v1/admin/save-states").with(ADMIN))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].cartridgeId").value("yellow"));

        mvc.perform(post("/api/v1/admin/save-states/" + id + "/load").with(ADMIN).with(csrf())).andExpect(status().isOk());
        verify(host).loadState(new byte[] {1, 2, 3});
        mvc.perform(post("/api/v1/admin/save-states/manual-20000101-000000-000000/load").with(ADMIN).with(csrf()))
                .andExpect(status().isNotFound());

        // Shutdown hook takes an automatic state.
        consoleService.saveOnShutdown();
        mvc.perform(get("/api/v1/admin/save-states").with(ADMIN))
                .andExpect(jsonPath("$[0].kind").value("auto"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void autosavePrunesOnlyAutos() throws Exception {
        settings.save(new Settings.Autosave(false, "0 0/30 * * * ?", 2));
        console(EmulatorHost.Power.ON, "gold");
        when(host.saveState()).thenReturn(new byte[] {9});

        mvc.perform(post("/api/v1/admin/save-states").with(ADMIN).with(csrf())).andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/admin/autosave/run").with(ADMIN).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("auto"));
        }
        mvc.perform(get("/api/v1/admin/save-states").with(ADMIN))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.kind=='manual')]").isNotEmpty());
        try (var files = Files.list(DATA.resolve("states/gold"))) {
            assertThat(files.count()).isEqualTo(3);
        }

        console(EmulatorHost.Power.OFF, "gold");
        mvc.perform(post("/api/v1/admin/autosave/run").with(ADMIN).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void autosaveSettingsRescheduleLive() throws Exception {
        TriggerKey key = TriggerKey.triggerKey("autosave");
        mvc.perform(put("/api/v1/admin/settings/autosave").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cron\":\"0 15 3 * * ?\",\"keep\":10}"))
                .andExpect(status().isOk());
        assertThat(((CronTrigger) scheduler.getTrigger(key)).getCronExpression()).isEqualTo("0 15 3 * * ?");

        mvc.perform(put("/api/v1/admin/settings/autosave").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cron\":\"not a cron\",\"keep\":10}"))
                .andExpect(status().isBadRequest());
        assertThat(settings.autosave().cron()).isEqualTo("0 15 3 * * ?");

        mvc.perform(put("/api/v1/admin/settings/autosave").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"cron\":\"0 15 3 * * ?\",\"keep\":10}"))
                .andExpect(status().isOk());
        assertThat(scheduler.getTrigger(key)).isNull();
    }
}
