package com.parsernews.web;

import com.parsernews.service.TelegramRuntimeSettingsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminTelegramSettingsController {
    private final TelegramRuntimeSettingsService settingsService;

    public AdminTelegramSettingsController(TelegramRuntimeSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/admin/telegram-settings")
    public TelegramSettingsResponse settings() {
        return response(settingsService.effectiveSettings());
    }

    @PutMapping("/api/admin/telegram-settings")
    public TelegramSettingsResponse update(@RequestBody TelegramSettingsRequest request) {
        return response(settingsService.update(request.enabled(), request.botToken(), request.chatId()));
    }

    @DeleteMapping("/api/admin/telegram-settings/runtime")
    public TelegramSettingsResponse clearRuntimeSettings() {
        return response(settingsService.clearRuntimeSettingsAndDisable());
    }

    private TelegramSettingsResponse response(TelegramRuntimeSettingsService.EffectiveTelegramSettings settings) {
        return new TelegramSettingsResponse(
                settings.enabled(),
                settings.configured(),
                settings.tokenSource(),
                settings.chatIdSource(),
                settings.tokenMasked(),
                settings.chatIdMasked(),
                settings.message()
        );
    }

    public record TelegramSettingsRequest(
            boolean enabled,
            String botToken,
            String chatId
    ) {
    }

    public record TelegramSettingsResponse(
            boolean enabled,
            boolean configured,
            TelegramRuntimeSettingsService.SecretSource tokenSource,
            TelegramRuntimeSettingsService.SecretSource chatIdSource,
            String tokenMasked,
            String chatIdMasked,
            String message
    ) {
    }
}
