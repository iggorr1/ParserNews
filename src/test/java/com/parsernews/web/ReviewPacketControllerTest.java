package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.ReviewPacketService;
import com.parsernews.service.SafetyGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewPacketController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class ReviewPacketControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewPacketService reviewPacketService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void reviewPacketRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/admin/review-packet.md"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markdownEndpointReturnsDownloadableMarkdown() throws Exception {
        when(reviewPacketService.markdown()).thenReturn("""
                # ParserNews Review Packet

                ## App Status
                ## Top Deal Groups
                """);

        mockMvc.perform(get("/api/admin/review-packet.md")
                        .with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("parsernews-review-packet.md")))
                .andExpect(content().string(containsString("## App Status")))
                .andExpect(content().string(containsString("## Top Deal Groups")));
    }
}
