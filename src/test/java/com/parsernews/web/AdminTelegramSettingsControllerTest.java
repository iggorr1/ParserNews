package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.TelegramRuntimeSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTelegramSettingsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class AdminTelegramSettingsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramRuntimeSettingsService settingsService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void settingsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/admin/telegram-settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/telegram-settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/telegram-settings/runtime"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSettingsDoesNotExposeFullTokenOrChatId() throws Exception {
        when(settingsService.effectiveSettings()).thenReturn(settings(
                true,
                true,
                "123456:full-telegram-token-secret",
                "987654321",
                TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                "123...cret",
                "987...4321"
        ));

        mockMvc.perform(get("/api/admin/telegram-settings").with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.tokenSource").value("RUNTIME"))
                .andExpect(jsonPath("$.chatIdSource").value("RUNTIME"))
                .andExpect(jsonPath("$.tokenMasked").value("123...cret"))
                .andExpect(jsonPath("$.chatIdMasked").value("987...4321"))
                .andExpect(content().string(not(containsString("full-telegram-token-secret"))))
                .andExpect(content().string(not(containsString("987654321"))));
    }

    @Test
    void putWithTokenEnablesRuntimeSettingsWithoutEchoingSecret() throws Exception {
        when(settingsService.update(true, "123456:full-telegram-token-secret", "987654321"))
                .thenReturn(settings(
                        true,
                        true,
                        "123456:full-telegram-token-secret",
                        "987654321",
                        TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                        TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                        "123...cret",
                        "987...4321"
                ));

        mockMvc.perform(put("/api/admin/telegram-settings")
                        .with(user("tester").roles("ADMIN", "VIEWER"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": true,
                                  "botToken": "123456:full-telegram-token-secret",
                                  "chatId": "987654321"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.tokenMasked").value("123...cret"))
                .andExpect(content().string(not(containsString("full-telegram-token-secret"))))
                .andExpect(content().string(not(containsString("987654321"))));
    }

    @Test
    void deleteClearsRuntimeTelegramSettings() throws Exception {
        when(settingsService.clearRuntimeSettingsAndDisable()).thenReturn(settings(
                false,
                false,
                "",
                "",
                TelegramRuntimeSettingsService.SecretSource.NONE,
                TelegramRuntimeSettingsService.SecretSource.NONE,
                null,
                null
        ));

        mockMvc.perform(delete("/api/admin/telegram-settings/runtime").with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.tokenSource").value("NONE"));
    }

    private TelegramRuntimeSettingsService.EffectiveTelegramSettings settings(
            boolean enabled,
            boolean configured,
            String botToken,
            String chatId,
            TelegramRuntimeSettingsService.SecretSource tokenSource,
            TelegramRuntimeSettingsService.SecretSource chatIdSource,
            String tokenMasked,
            String chatIdMasked
    ) {
        return new TelegramRuntimeSettingsService.EffectiveTelegramSettings(
                enabled,
                configured,
                botToken,
                chatId,
                tokenSource,
                chatIdSource,
                tokenMasked,
                chatIdMasked,
                enabled ? "Telegram is enabled." : "Telegram is disabled."
        );
    }
}
