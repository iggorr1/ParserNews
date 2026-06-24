package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.OpenAiRuntimeSettingsService;
import com.parsernews.service.SafetyGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOpenAiSettingsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class AdminOpenAiSettingsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAiRuntimeSettingsService settingsService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void settingsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/admin/openai-settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/openai-settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/openai-settings/runtime-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSettingsDoesNotExposeFullKey() throws Exception {
        when(settingsService.effectiveSettings()).thenReturn(settings(
                true,
                true,
                "test-openai-secret-full-key-1234abcd",
                OpenAiRuntimeSettingsService.KeySource.RUNTIME,
                "tes...abcd"
        ));

        mockMvc.perform(get("/api/admin/openai-settings").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.keySource").value("RUNTIME"))
                .andExpect(jsonPath("$.keyMasked").value("tes...abcd"))
                .andExpect(content().string(not(containsString("test-openai-secret-full-key-1234abcd"))));
    }

    @Test
    void putWithKeyEnablesRuntimeSettings() throws Exception {
        when(settingsService.update(true, "test-runtime-openai-key-1234abcd", "gpt-4.1-mini", 12000))
                .thenReturn(settings(
                        true,
                        true,
                        "test-runtime-openai-key-1234abcd",
                        OpenAiRuntimeSettingsService.KeySource.RUNTIME,
                        "tes...abcd"
                ));

        mockMvc.perform(put("/api/admin/openai-settings")
                        .with(httpBasic("tester", "secret"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": true,
                                  "apiKey": "test-runtime-openai-key-1234abcd",
                                  "model": "gpt-4.1-mini",
                                  "maxInputChars": 12000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.keySource").value("RUNTIME"))
                .andExpect(jsonPath("$.keyMasked").value("tes...abcd"))
                .andExpect(content().string(not(containsString("test-runtime-openai-key-1234abcd"))));
    }

    @Test
    void deleteClearsRuntimeKey() throws Exception {
        when(settingsService.clearRuntimeKeyAndDisable()).thenReturn(settings(
                false,
                false,
                "",
                OpenAiRuntimeSettingsService.KeySource.NONE,
                null
        ));

        mockMvc.perform(delete("/api/admin/openai-settings/runtime-key").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.keySource").value("NONE"));
    }

    private OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings(
            boolean enabled,
            boolean configured,
            String apiKey,
            OpenAiRuntimeSettingsService.KeySource keySource,
            String keyMasked
    ) {
        return new OpenAiRuntimeSettingsService.EffectiveOpenAiSettings(
                enabled,
                configured,
                apiKey,
                "gpt-4.1-mini",
                12000,
                keySource,
                keyMasked,
                enabled ? "OpenAI AI Review is enabled." : "OpenAI AI Review is disabled."
        );
    }
}
