package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.DealGroupingService;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DealGroupController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class DealGroupControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealGroupingService dealGroupingService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void dealGroupsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/deal-groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dealGroupsReturnGroupedSignals() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.groups(null, null, 50)).thenReturn(List.of(group));

        mockMvc.perform(get("/api/deal-groups").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$[0].targetTicker").value("APGE"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].relatedSignals.length()").value(2))
                .andExpect(jsonPath("$[0].relatedSignals[0].sourceType").value("RSS_NEWS"));
    }

    @Test
    void dealGroupDetailReturnsOneGroup() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/deal-groups/target-ticker:APGE").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$.relatedSignals.length()").value(2));
    }

    @Test
    void dealGroupDetailReturns404ForMissingGroup() throws Exception {
        when(dealGroupingService.group("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/deal-groups/missing").with(httpBasic("tester", "secret")))
                .andExpect(status().isNotFound());
    }

    private DealGroupingService.DealGroupResponse group() {
        return new DealGroupingService.DealGroupResponse(
                "target-ticker:APGE",
                SignalInboxController.SourceType.RSS_NEWS,
                1L,
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "APGE",
                "1550760",
                "ABBV",
                "1551152",
                SignalInboxController.UnifiedPriority.HIGH,
                com.parsernews.model.DealRelevance.PUBLIC_CASH_ACQUISITION,
                com.parsernews.model.Tradability.HIGH,
                com.parsernews.model.DealStage.DEFINITIVE_AGREEMENT,
                com.parsernews.model.DealTiming.EARLY,
                ManualReviewStatus.PENDING,
                ManualReviewReason.GOOD_SIGNAL,
                List.of(
                        new DealGroupingService.RelatedSignalResponse(
                                SignalInboxController.SourceType.RSS_NEWS,
                                1L,
                                "AbbVie to Acquire Apogee",
                                "https://example.test/rss",
                                Instant.parse("2026-06-20T12:00:00Z"),
                                null,
                                "ACQUISITION",
                                SignalInboxController.UnifiedPriority.HIGH,
                                "primary RSS deal signal"
                        ),
                        new DealGroupingService.RelatedSignalResponse(
                                SignalInboxController.SourceType.SEC_FILING,
                                10L,
                                "APOGEE THERAPEUTICS INC 8-K",
                                "https://sec.gov/test",
                                Instant.parse("2026-06-20T12:01:00Z"),
                                java.time.LocalDate.of(2026, 6, 20),
                                "MERGER_AGREEMENT",
                                SignalInboxController.UnifiedPriority.HIGH,
                                "same target CIK"
                        )
                ),
                List.of("https://example.test/rss", "https://sec.gov/test"),
                List.of("Multiple related signals found for this deal"),
                Instant.parse("2026-06-20T12:01:00Z")
        );
    }
}
