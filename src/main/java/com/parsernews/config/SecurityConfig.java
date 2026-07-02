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
    SecurityFilterChain securityFilterChain(HttpSecurity http, ParserNewsAuthProperties authProperties) throws Exception {
        if (!authProperties.enabled()) {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/error", "/login.html").permitAll()
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
                // API/XHR clients get 401 instead of a 302 redirect to the HTML login page,
                // so front-end fetch() error handling can react correctly. Browser navigation
                // to protected HTML pages falls through to the login-page redirect (the default).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(apiAwareEntryPoint()))
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .build();
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
