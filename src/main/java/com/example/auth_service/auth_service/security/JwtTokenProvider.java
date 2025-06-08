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

    // SecretKey 객체 생성 메소드 (중복 코드 제거를 위해)
    private Key getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
    }

    public String createToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        // UserPrincipal 대신 User로 처리하거나 조건부 처리
        Long userId = null;
        Integer loginType = 0; // 기본값: 일반 로그인
        
        if (authentication.getPrincipal() instanceof UserPrincipal) {
            userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        } else {
            // 사용자 이메일로 사용자 ID 조회
            String email = authentication.getName();
            User user = userDetailsService.getUserByEmail(email);
            if (user != null) {
                userId = user.getUserNo();
                loginType = user.getLoginType(); // 로그인 타입 추가
            }
        }

        long now = (new Date()).getTime();
        Date validity = new Date(now + this.getTokenValidityInMilliseconds());

        Key signingKey = getSigningKey();

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .claim("userId", userId)
                .claim("loginType", loginType) // 로그인 타입 추가
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createToken(String username) {
        Claims claims = Jwts.claims().setSubject(username);

        // 사용자 ID를 DB에서 조회
        User user = userDetailsService.getUserByEmail(username);
        if (user != null) {
            claims.put("userId", user.getUserNo());
            claims.put("loginType", user.getLoginType()); // 로그인 타입 추가
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getTokenValidityInMilliseconds());

        Key signingKey = getSigningKey();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createRefreshToken(String username) {
        Claims claims = Jwts.claims().setSubject(username);

        // 사용자 ID를 DB에서 조회
        User user = userDetailsService.getUserByEmail(username);
        if (user != null) {
            claims.put("userId", user.getUserNo());
            claims.put("loginType", user.getLoginType()); // 로그인 타입 추가
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getRefreshTokenValidityInMilliseconds());

        Key signingKey = getSigningKey();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 핵심 수정: 소셜 로그인 사용자 구분 처리
    public Authentication getAuthentication(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            log.info("Token Claims: {}", claims);
            String email = claims.getSubject();
            log.info("Email from token: {}", email);

            // 토큰에서 로그인 타입 확인
            Integer loginType = claims.get("loginType", Integer.class);
            log.info("Login type from token: {}", loginType);

            // 소셜 로그인 사용자(loginType=1)는 UserDetailsService 거치지 않음
            if (loginType != null && loginType == 1) {
                log.info("소셜 로그인 사용자 인증 처리: {}", email);
                
                // 소셜 로그인 사용자용 UserDetails 직접 생성
                UserDetails socialUserDetails = new org.springframework.security.core.userdetails.User(
                    email,
                    "", // 비밀번호 없음
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );

                return new UsernamePasswordAuthenticationToken(
                        socialUserDetails,
                        token,
                        socialUserDetails.getAuthorities());
            } else {
                // 일반 로그인 사용자는 기존 방식대로 처리
                log.info("일반 로그인 사용자 인증 처리: {}", email);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                log.info("UserDetails: {}", userDetails);

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
            log.info("Validating token: {}", token.substring(0, Math.min(10, token.length())) + "...");
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            log.info("Token is valid");
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

    public String createSocialLoginToken(String email) {
        Claims claims = Jwts.claims().setSubject(email);

        // 소셜 로그인 사용자를 위한 기본 권한 추가
        claims.put("auth", "ROLE_USER");
        claims.put("loginType", 1); // 소셜 로그인 타입 명시

        // 사용자 ID를 DB에서 조회
        User user = userDetailsService.getUserByEmail(email);
        if (user != null) {
            claims.put("userId", user.getUserNo());
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + getTokenValidityInMilliseconds());

        Key signingKey = getSigningKey();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        // userId 클레임이 있는지 확인하고 반환
        return claims.get("userId", Long.class);
    }
}