package com.parsernews.web;

import com.parsernews.service.StatusService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/status")
    public StatusService.StatusResponse status() {
        return statusService.status();
    }

    @GetMapping("/api/me")
    public MeResponse me(Authentication auth) {
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        return new MeResponse(auth != null ? auth.getName() : "anonymous", isAdmin);
    }

    public record MeResponse(String username, boolean admin) {}
}
