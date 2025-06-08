package com.example.auth_service.auth_service.security;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.example.auth_service.auth_service.domain.User;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Component
public class JwtTokenProvider {
    private String secretKey;
    private long accessTokenValidityInSeconds;
    private long refreshTokenValidityInSeconds;
    private final CustomUserDetailsService userDetailsService;

    public JwtTokenProvider(
            String jwtSecret,
            long accessTokenValidity,
            long refreshTokenValidity,
            CustomUserDetailsService userDetailsService) {
        this.secretKey = jwtSecret;
        this.accessTokenValidityInSeconds = accessTokenValidity;
        this.refreshTokenValidityInSeconds = refreshTokenValidity;
        this.userDetailsService = userDetailsService;
    }

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    private Key getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
    }

    // ✅ 성능 최적화: Authentication에서 바로 정보 추출
    public String createToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Long userId = null;
        Integer loginType = 0;
        
        if (authentication.getPrincipal() instanceof UserPrincipal) {
            userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        } else {
            // ✅ 한 번만 DB 조회
            String email = authentication.getName();
            User user = userDetailsService.getUserByEmail(email);
            if (user != null) {
                userId = user.getUserNo();
                loginType = user.getLoginType();
            }
        }

        long now = (new Date()).getTime();
        Date validity = new Date(now + this.getTokenValidityInMilliseconds());

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .claim("userId", userId)
                .claim("loginType", loginType)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 성능 최적화: 파라미터로 정보 받기 (DB 조회 없음)
    public String createToken(String username, Long userId, Integer loginType) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("userId", userId);
        claims.put("loginType", loginType != null ? loginType : 0);

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 기존 호환성을 위한 메서드 (deprecated)
    public String createToken(String username) {
        // 기본값으로 처리 (DB 조회 최소화)
        return createToken(username, null, 0);
    }

    // ✅ 성능 최적화: 파라미터로 정보 받기 (DB 조회 없음)
    public String createRefreshToken(String username, Long userId, Integer loginType) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("userId", userId);
        claims.put("loginType", loginType != null ? loginType : 0);

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getRefreshTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 기존 호환성을 위한 메서드 (deprecated)
    public String createRefreshToken(String username) {
        return createRefreshToken(username, null, 0);
    }

    // ✅ 핵심 성능 개선: DB 조회 완전 제거
    public Authentication getAuthentication(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String email = claims.getSubject();
            Integer loginType = claims.get("loginType", Integer.class);

            // ✅ 소셜 로그인 사용자는 DB 조회 없이 바로 처리
            if (loginType != null && loginType == 1) {
                UserDetails socialUserDetails = new org.springframework.security.core.userdetails.User(
                    email,
                    "",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );

                return new UsernamePasswordAuthenticationToken(
                        socialUserDetails,
                        token,
                        socialUserDetails.getAuthorities());
            } else {
                // ✅ 일반 로그인 사용자만 DB 조회
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                return new UsernamePasswordAuthenticationToken(
                        userDetails,
                        token,
                        userDetails.getAuthorities());
            }
        } catch (Exception e) {
            log.error("Authentication Error", e);
            throw new RuntimeException("Invalid token", e);
        }
    }

    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public long getExpirationFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getExpiration().getTime() - new Date().getTime();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public long getTokenValidityInMilliseconds() {
        return accessTokenValidityInSeconds * 1000;
    }

    public long getRefreshTokenValidityInMilliseconds() {
        return refreshTokenValidityInSeconds * 1000;
    }

    // ✅ 성능 최적화: 파라미터로 정보 받기 (DB 조회 없음)
    public String createSocialLoginToken(String email, Long userId) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("auth", "ROLE_USER");
        claims.put("loginType", 1); // 소셜 로그인 타입
        claims.put("userId", userId);

        Date now = new Date();
        Date validity = new Date(now.getTime() + getTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 기존 호환성을 위한 메서드 (deprecated - DB 조회함)
    public String createSocialLoginToken(String email) {
        User user = userDetailsService.getUserByEmail(email);
        Long userId = user != null ? user.getUserNo() : null;
        return createSocialLoginToken(email, userId);
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("userId", Long.class);
    }
}