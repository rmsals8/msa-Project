package com.example.api_gateway.filter;

import io.jsonwebtoken.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import javax.crypto.spec.SecretKeySpec;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = Logger.getLogger(AuthenticationFilter.class.getName());

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private final List<String> excludedPaths = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/social",
            "/api/v1/auth/email-verify-request",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/complete-signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/validate-token",
            "/api/v1/auth/password",
            "/api/v1/places/search",
            "/api/v1/schedules/**",
            "/api/v1/visit-histories");

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            logger.info("Processing request: " + path);

            // 인증이 필요없는 API는 바로 통과시킴
            if (isPathExcluded(path)) {
                logger.info("Path excluded from authentication: " + path);
                return chain.filter(exchange);
            }

            // Authorization 헤더 확인
            if (!request.getHeaders().containsKey("Authorization")) {
                logger.warning("No Authorization header found for path: " + path);
                return onError(exchange, "No Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warning("Invalid Authorization header format for path: " + path);
                return onError(exchange, "Invalid Authorization header format", HttpStatus.UNAUTHORIZED);
            }

            // JWT 토큰 검증
            String token = authHeader.substring(7);
            try {
                // 토큰 검증
                Claims claims = validateAndExtractClaims(token);

                // 사용자 정보 추출
                String userId = claims.getSubject();

                logger.info("Successfully validated token for user: " + userId);

                // 검증된 사용자 정보를 헤더에 추가
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .build();

                logger.info("Added X-User-Id header: " + userId);

                // 변경된 요청으로 체인 계속 진행
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (ExpiredJwtException e) {
                logger.warning("Token expired: " + e.getMessage());
                return onError(exchange, "Expired JWT token", HttpStatus.UNAUTHORIZED);
            } catch (JwtException | IllegalArgumentException e) {
                logger.warning("Invalid token: " + e.getMessage());
                return onError(exchange, "Invalid JWT token: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isPathExcluded(String path) {
        // 더 정확하게 로깅 추가
        logger.info("Checking path exclusion for: " + path);
        boolean excluded = excludedPaths.stream().anyMatch(excludedPath -> {
            boolean matches = path.startsWith(excludedPath);
            logger.info("Path " + path + " matches " + excludedPath + "? " + matches);
            return matches;
        });
        logger.info("Path " + path + " is excluded? " + excluded);
        return excluded;
    }

    private Claims validateAndExtractClaims(String token) {
        try {
            // Auth Service에서 사용하는 방식과 동일하게 키 생성
            // 1. Base64로 인코딩된 시크릿 키 사용 (Auth Service와 동일한 방식)
            String encodedKey = Base64.getEncoder().encodeToString(jwtSecret.getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = encodedKey.getBytes(StandardCharsets.UTF_8);
            Key key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());

            logger.info("Attempting to validate token with HS512 algorithm");

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            logger.info("Extracted claims from token: " + claims.getSubject());
            return claims;
        } catch (Exception e) {
            logger.severe("Error validating token: " + e.getMessage());
            throw new JwtException("Invalid token: " + e.getMessage());
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        logger.warning("Authentication error: " + err);
        return response.setComplete();
    }

    public static class Config {
        // 필요한 설정 속성을 추가할 수 있음
    }
}