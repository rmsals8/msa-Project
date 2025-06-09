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

    // ✅ 수정: Authentication에서 토큰 생성할 때 반드시 userId 포함
    public String createToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Long userId = null;
        Integer loginType = 0;

        if (authentication.getPrincipal() instanceof UserPrincipal) {
            userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        } else {
            // DB에서 사용자 정보 조회해서 userId 가져오기
            String email = authentication.getName();
            User user = userDetailsService.getUserByEmail(email);
            if (user != null) {
                userId = user.getUserNo();
                loginType = user.getLoginType();
                log.debug("토큰 생성 - DB에서 userId 조회: {} -> {}", email, userId);
            } else {
                log.warn("토큰 생성 실패 - 사용자를 찾을 수 없음: {}", email);
            }
        }

        long now = (new Date()).getTime();
        Date validity = new Date(now + this.getTokenValidityInMilliseconds());

        // ✅ 핵심: Claims에 userId 반드시 포함!
        Claims claims = Jwts.claims().setSubject(authentication.getName());
        claims.put("auth", authorities);
        claims.put("userId", userId); // ✅ 이 부분이 제일 중요!
        claims.put("loginType", loginType);

        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        log.info("✅ 토큰 생성 완료 - email: {}, userId: {}, loginType: {}",
                authentication.getName(), userId, loginType);

        return token;
    }

    // ✅ 수정: 파라미터로 정보 받는 버전 - userId 반드시 포함
    public String createToken(String username, Long userId, Integer loginType) {
        if (userId == null) {
            log.warn("❌ userId가 null입니다. 토큰 생성 실패 가능성 있음: {}", username);
        }

        Claims claims = Jwts.claims().setSubject(username);
        claims.put("userId", userId); // ✅ userId 반드시 포함!
        claims.put("loginType", loginType != null ? loginType : 0);
        claims.put("auth", "ROLE_USER");

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getTokenValidityInMilliseconds());

        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        log.info("✅ 파라미터 토큰 생성 완료 - username: {}, userId: {}, loginType: {}",
                username, userId, loginType);

        return token;
    }

    // ✅ 기존 호환성을 위한 메서드 - DB 조회해서 userId 찾기
    public String createToken(String username) {
        User user = userDetailsService.getUserByEmail(username);
        Long userId = user != null ? user.getUserNo() : null;
        Integer loginType = user != null ? user.getLoginType() : 0;

        log.debug("기존 방식 토큰 생성 - username: {}, userId: {}", username, userId);
        return createToken(username, userId, loginType);
    }

    // ✅ 수정: 리프레시 토큰에도 userId 포함
    public String createRefreshToken(String username, Long userId, Integer loginType) {
        if (userId == null) {
            log.warn("❌ 리프레시 토큰: userId가 null입니다: {}", username);
        }

        Claims claims = Jwts.claims().setSubject(username);
        claims.put("userId", userId); // ✅ 리프레시 토큰에도 userId 포함!
        claims.put("loginType", loginType != null ? loginType : 0);

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getRefreshTokenValidityInMilliseconds());

        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        log.info("✅ 리프레시 토큰 생성 완료 - username: {}, userId: {}", username, userId);
        return token;
    }

    // ✅ 기존 호환성을 위한 메서드
    public String createRefreshToken(String username) {
        User user = userDetailsService.getUserByEmail(username);
        Long userId = user != null ? user.getUserNo() : null;
        Integer loginType = user != null ? user.getLoginType() : 0;

        return createRefreshToken(username, userId, loginType);
    }

    // ✅ 수정: 소셜 로그인 토큰에도 userId 반드시 포함
    public String createSocialLoginToken(String email, Long userId) {
        if (userId == null) {
            log.warn("❌ 소셜 로그인 토큰: userId가 null입니다: {}", email);
        }

        Claims claims = Jwts.claims().setSubject(email);
        claims.put("auth", "ROLE_USER");
        claims.put("loginType", 1); // 소셜 로그인 타입
        claims.put("userId", userId); // ✅ 소셜 로그인에도 userId 포함!

        Date now = new Date();
        Date validity = new Date(now.getTime() + getTokenValidityInMilliseconds());

        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        log.info("✅ 소셜 로그인 토큰 생성 완료 - email: {}, userId: {}", email, userId);
        return token;
    }

    // ✅ 기존 호환성을 위한 메서드 (deprecated - DB 조회함)
    public String createSocialLoginToken(String email) {
        User user = userDetailsService.getUserByEmail(email);
        Long userId = user != null ? user.getUserNo() : null;

        log.debug("기존 방식 소셜 토큰 생성 - email: {}, userId: {}", email, userId);
        return createSocialLoginToken(email, userId);
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
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

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

    // ✅ userId 추출 메서드 - 로그 추가
    public Long getUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = claims.get("userId", Long.class);
            log.debug("토큰에서 userId 추출: {}", userId);

            if (userId == null) {
                log.warn("❌ 토큰에 userId claim이 없습니다!");
            }

            return userId;
        } catch (Exception e) {
            log.error("토큰에서 userId 추출 실패", e);
            return null;
        }
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
}