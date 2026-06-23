package com.parsernews.web;

import com.parsernews.service.OpenAiRuntimeSettingsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOpenAiSettingsController {
    private final OpenAiRuntimeSettingsService settingsService;

    public AdminOpenAiSettingsController(OpenAiRuntimeSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/admin/openai-settings")
    public OpenAiSettingsResponse settings() {
        return response(settingsService.effectiveSettings());
    }

    @PutMapping("/api/admin/openai-settings")
    public OpenAiSettingsResponse update(@RequestBody OpenAiSettingsRequest request) {
        return response(settingsService.update(
                request.enabled(),
                request.apiKey(),
                request.model(),
                request.maxInputChars()
        ));
    }

    @DeleteMapping("/api/admin/openai-settings/runtime-key")
    public OpenAiSettingsResponse clearRuntimeKey() {
        return response(settingsService.clearRuntimeKeyAndDisable());
    }

    private OpenAiSettingsResponse response(OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings) {
        return new OpenAiSettingsResponse(
                settings.enabled(),
                settings.configured(),
                settings.model(),
                settings.maxInputChars(),
                settings.keySource(),
                settings.keyMasked(),
                settings.message()
        );
    }

    public record OpenAiSettingsRequest(
            boolean enabled,
            String apiKey,
            String model,
            Integer maxInputChars
    ) {
    }

    public record OpenAiSettingsResponse(
            boolean enabled,
            boolean configured,
            String model,
            int maxInputChars,
            OpenAiRuntimeSettingsService.KeySource keySource,
            String keyMasked,
            String message
    ) {
    }
}
