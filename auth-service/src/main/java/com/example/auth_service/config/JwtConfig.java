package com.example.auth_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.auth_service.security.CustomUserDetailsService;
import com.example.auth_service.security.JwtTokenProvider;

@Configuration
public class JwtConfig {
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-validity-in-seconds}")
    private long accessTokenValidity;

    @Value("${app.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenValidity;

    @Bean
    public JwtTokenProvider jwtTokenProvider(CustomUserDetailsService userDetailsService) {
        return new JwtTokenProvider(
                jwtSecret,
                accessTokenValidity,
                refreshTokenValidity,
                userDetailsService);
    }
}