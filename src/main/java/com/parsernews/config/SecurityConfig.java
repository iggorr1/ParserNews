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
@EnableConfigurationProperties(SecurityConfig.ParserNewsAuthProperties.class)
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
                        .requestMatchers("/", "/index.html", "/api/**", "/actuator/**").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(ParserNewsAuthProperties authProperties, PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername(authProperties.username())
                .password(passwordEncoder.encode(authProperties.password()))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @ConfigurationProperties(prefix = "parsernews.auth")
    public record ParserNewsAuthProperties(
            boolean enabled,
            String username,
            String password
    ) {
        public ParserNewsAuthProperties {
            username = (username == null || username.isBlank()) ? "admin" : username;
            password = (password == null || password.isBlank()) ? "admin" : password;
        }
    }
}
