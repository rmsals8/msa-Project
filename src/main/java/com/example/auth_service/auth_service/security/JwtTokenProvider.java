package com.example.auth_service.auth_service.security;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.example.auth_service.auth_service.domain.User;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Component
public class JwtTokenProvider {
    private String secretKey;
    private long accessTokenValidityInSeconds;
    private long refreshTokenValidityInSeconds;
    private final CustomUserDetailsService userDetailsService;
    
    // ✅ 성능 최적화: 파싱된 토큰 캐시 (메모리)
    private final ConcurrentHashMap<String, Claims> tokenCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000; // 최대 캐시 크기

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

    // ✅ 성능 최적화: SecretKey 객체 캐시
    private Key signingKey;
    private Key getSigningKey() {
        if (signingKey == null) {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            signingKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
        }
        return signingKey;
    }

    public String createToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        // UserPrincipal 대신 User로 처리하거나 조건부 처리
        Long userId = null;
        if (authentication.getPrincipal() instanceof UserPrincipal) {
            userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        } else {
            // 사용자 이메일로 사용자 ID 조회
            String email = authentication.getName();
            User user = userDetailsService.getUserByEmail(email);
            if (user != null) {
                userId = user.getUserNo();
            }
        }

        long now = (new Date()).getTime();
        Date validity = new Date(now + this.getTokenValidityInMilliseconds());

        Key signingKey = getSigningKey();

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .claim("userId", userId)
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createToken(String username) {
        Claims claims = Jwts.claims().setSubject(username);

        // ✅ 캐시된 사용자 정보 활용
        User user = getUserFromCacheOrDB(username);
        if (user != null) {
            claims.put("userId", user.getUserNo());
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String createRefreshToken(String username) {
        Claims claims = Jwts.claims().setSubject(username);

        // ✅ 캐시된 사용자 정보 활용
        User user = getUserFromCacheOrDB(username);
        if (user != null) {
            claims.put("userId", user.getUserNo());
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + this.getRefreshTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ✅ 성능 최적화: 토큰 파싱 캐시 적용
    private Claims parseToken(String token) {
        // 캐시에서 먼저 확인
        Claims cachedClaims = tokenCache.get(token);
        if (cachedClaims != null) {
            // 만료 시간 확인
            if (cachedClaims.getExpiration().after(new Date())) {
                return cachedClaims;
            } else {
                // 만료된 토큰은 캐시에서 제거
                tokenCache.remove(token);
            }
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 캐시 크기 관리
            if (tokenCache.size() >= MAX_CACHE_SIZE) {
                // 가장 오래된 항목 일부 제거 (간단한 구현)
                tokenCache.clear();
            }

            // 캐시에 저장 (토큰이 유효한 경우만)
            tokenCache.put(token, claims);
            return claims;

        } catch (Exception e) {
            log.debug("토큰 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    // ✅ 성능 최적화: 빠른 인증 객체 생성
    public Authentication getAuthentication(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                throw new RuntimeException("Invalid token");
            }

            String email = claims.getSubject();
            log.debug("토큰에서 추출한 이메일: {}", email);

            // ✅ 소셜 로그인 사용자를 위한 빠른 경로
            UserDetails userDetails = loadUserDetailsOptimized(email);

            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    token,
                    userDetails.getAuthorities());
        } catch (Exception e) {
            log.error("Authentication Error", e);
            throw new RuntimeException("Invalid token", e);
        }
    }

    // ✅ 성능 최적화: UserDetails 로드 최적화
    private UserDetails loadUserDetailsOptimized(String email) {
        try {
            return userDetailsService.loadUserByUsername(email);
        } catch (Exception e) {
            log.error("UserDetails 로드 실패: {}", e.getMessage());
            throw e;
        }
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    public long getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.getExpiration().getTime() - new Date().getTime();
        }
        return 0;
    }

    // ✅ 성능 최적화: 토큰 유효성 검사 간소화
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return false;
            }
            
            // 만료 시간만 간단히 체크
            boolean isValid = claims.getExpiration().after(new Date());
            if (!isValid) {
                // 만료된 토큰은 캐시에서 제거
                tokenCache.remove(token);
            }
            return isValid;

        } catch (Exception ex) {
            log.debug("토큰 검증 실패: {}", ex.getMessage());
            return false;
        }
    }

    public long getTokenValidityInMilliseconds() {
        return accessTokenValidityInSeconds * 1000;
    }

    public long getRefreshTokenValidityInMilliseconds() {
        return refreshTokenValidityInSeconds * 1000;
    }

    public String createSocialLoginToken(String email) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("auth", "ROLE_USER");

        // ✅ 캐시된 사용자 정보 활용
        User user = getUserFromCacheOrDB(email);
        if (user != null) {
            claims.put("userId", user.getUserNo());
        }

        Date now = new Date();
        Date validity = new Date(now.getTime() + getTokenValidityInMilliseconds());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Long.class) : null;
    }

    // ✅ 사용자 정보 캐시 활용
    @Cacheable(value = "users", key = "#email")
    private User getUserFromCacheOrDB(String email) {
        return userDetailsService.getUserByEmail(email);
    }

    // ✅ 캐시 정리 메서드 (필요시 호출)
    public void clearTokenCache() {
        tokenCache.clear();
        log.info("토큰 캐시가 정리되었습니다.");
    }

    // ✅ 캐시 통계 조회 (모니터링용)
    public int getTokenCacheSize() {
        return tokenCache.size();
    }
}