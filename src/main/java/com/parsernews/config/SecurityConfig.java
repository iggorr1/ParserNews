package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

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
                        .requestMatchers(HttpMethod.GET, "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/logs.html").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.html", "/api/**", "/actuator/**").authenticated()
                        .anyRequest().hasRole("ADMIN")
                )
                .httpBasic(Customizer.withDefaults())
                .build();
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
