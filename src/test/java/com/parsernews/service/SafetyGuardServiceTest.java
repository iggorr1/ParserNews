package com.parsernews.service;

import com.parsernews.config.SafetySettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyGuardServiceTest {
    @Test
    void allowsSafeResearchOnlyConfiguration() {
        SafetyGuardService service = new SafetyGuardService(new SafetySettings(
                false,
                false,
                false,
                false,
                false
        ));

        assertThatCode(service::validateStartupSafety).doesNotThrowAnyException();
    }

    @Test
    void rejectsTradingConfiguration() {
        SafetyGuardService service = new SafetyGuardService(new SafetySettings(
                true,
                false,
                false,
                false,
                false
        ));

        assertThatThrownBy(service::validateStartupSafety)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not trade");
    }

    @Test
    void rejectsRealWebParsingForCurrentMvp() {
        SafetyGuardService service = new SafetyGuardService(new SafetySettings(
                false,
                false,
                false,
                false,
                true
        ));

        assertThatThrownBy(service::validateStartupSafety)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("real web parsing is disabled");
    }
}
