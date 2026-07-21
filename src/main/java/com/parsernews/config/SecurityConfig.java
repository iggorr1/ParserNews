package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

@Configuration
@EnableConfigurationProperties({
        SecurityConfig.ParserNewsAuthProperties.class,
        FullRefreshSchedulerSettings.class
})
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ParserNewsAuthProperties authProperties,
            @org.springframework.beans.factory.annotation.Value("${parsernews.remember-me.key:}") String rememberMeKey
    ) throws Exception {
        if (!authProperties.enabled()) {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/login.html").permitAll()
                        // Permit the error dispatch for every method. A controller that throws e.g.
                        // 401 triggers an internal forward to /error preserving the method; if POST
                        // /error is not permitted it gets redirected to login (302) instead of
                        // rendering the real status (breaks the API-key 401 on POST /api/export/**).
                        .requestMatchers("/error").permitAll()
                        // Read-only data export for external consumers — gated by its own API key
                        // inside the controller, not by the session login.
                        .requestMatchers(HttpMethod.GET, "/api/export/**").permitAll()
                        // POST /api/export/deals/{key}/recheck — gated by the same X-API-Key in the
                        // controller, so it must bypass the ADMIN-only rule for POST /api/** below.
                        .requestMatchers(HttpMethod.POST, "/api/export/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/logs.html").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.html", "/api/**", "/actuator/**").authenticated()
                        .anyRequest().hasRole("ADMIN")
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login.html?error")
                        .permitAll()
                )
                // Persistent login: a signed cookie re-authenticates after the session expires or the
                // app restarts (deploys drop in-memory sessions), so you stay logged in for 30 days
                // instead of re-entering the password constantly. alwaysRemember means no checkbox is
                // needed on the login form.
                .rememberMe(remember -> remember
                        .key(rememberMeKey(authProperties, rememberMeKey))
                        .rememberMeServices(rememberMeServices(authProperties, rememberMeKey))
                )
                // API/XHR clients get 401 instead of a 302 redirect to the HTML login page,
                // so front-end fetch() error handling can react correctly. Browser navigation
                // to protected HTML pages falls through to the login-page redirect (the default).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(apiAwareEntryPoint()))
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll()
                )
                .build();
    }

    /**
     * The key signing remember-me cookies. It must be stable across restarts or every cookie is
     * invalidated on boot; it derives from the admin password (stable per deployment, never logged)
     * unless {@code parsernews.remember-me.key} is set.
     */
    private static String rememberMeKey(ParserNewsAuthProperties authProperties, String configuredKey) {
        return configuredKey == null || configuredKey.isBlank()
                ? "pn-rm-" + authProperties.password()
                : configuredKey;
    }

    /**
     * Remember-me backed by a user store holding the <em>configured</em> passwords rather than the
     * BCrypt-encoded ones.
     *
     * <p>The token signature includes the user's password. The login user store re-encodes with
     * BCrypt at every startup, and BCrypt salts randomly, so that hash differs on each boot — which
     * silently invalidated every remember-me cookie on every deploy. The configured password is
     * stable, so cookies survive restarts. Login itself still authenticates against the BCrypt store.
     */
    private static TokenBasedRememberMeServices rememberMeServices(
            ParserNewsAuthProperties authProperties, String configuredKey) {
        InMemoryUserDetailsManager stableStore = new InMemoryUserDetailsManager(
                User.withUsername(authProperties.username())
                        .password(authProperties.password())
                        .roles("ADMIN", "VIEWER")
                        .build());
        if (authProperties.viewerUsername() != null && authProperties.viewerPassword() != null) {
            stableStore.createUser(User.withUsername(authProperties.viewerUsername())
                    .password(authProperties.viewerPassword())
                    .roles("VIEWER")
                    .build());
        }
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(rememberMeKey(authProperties, configuredKey), stableStore);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds(60 * 60 * 24 * 30);
        return services;
    }

    /**
     * Returns 401 for unauthenticated {@code /api/**} and {@code /actuator/**} requests (so XHR
     * clients can handle it), while all other (browser HTML) requests redirect to the login page.
     */
    private static AuthenticationEntryPoint apiAwareEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        entryPoints.put(new AntPathRequestMatcher("/actuator/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));
        return entryPoint;
    }

    @Bean
    UserDetailsService userDetailsService(ParserNewsAuthProperties authProperties, PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername(authProperties.username())
                .password(passwordEncoder.encode(authProperties.password()))
                .roles("ADMIN", "VIEWER")
                .build();
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(admin);
        if (authProperties.viewerUsername() != null && authProperties.viewerPassword() != null) {
            UserDetails viewer = User.withUsername(authProperties.viewerUsername())
                    .password(passwordEncoder.encode(authProperties.viewerPassword()))
                    .roles("VIEWER")
                    .build();
            manager.createUser(viewer);
        }
        return manager;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @ConfigurationProperties(prefix = "parsernews.auth")
    public record ParserNewsAuthProperties(
            boolean enabled,
            String username,
            String password,
            String viewerUsername,
            String viewerPassword
    ) {
        public ParserNewsAuthProperties {
            username = (username == null || username.isBlank()) ? "admin" : username;
            password = (password == null || password.isBlank()) ? "admin" : password;
            viewerUsername = (viewerUsername == null || viewerUsername.isBlank()) ? null : viewerUsername;
            viewerPassword = (viewerPassword == null || viewerPassword.isBlank()) ? null : viewerPassword;
        }
    }
}
