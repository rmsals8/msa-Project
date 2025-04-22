package com.example.schedule_service.filter;

import com.example.schedule_service.client.AuthServiceClient;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;

// UsageCheckFilter.java
// schedule-service/src/main/java/com/example/schedule_service/filter/UsageCheckFilter.java

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UsageCheckFilter implements Filter {

    private final AuthServiceClient authServiceClient;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (shouldCheckUsage(httpRequest.getRequestURI(), httpRequest.getMethod())) {
            String bearerToken = httpRequest.getHeader("Authorization");

            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write("{\"error\":\"인증 토큰이 없습니다.\"}");
                return;
            }

            try {
                // auth-service에 토큰 검증 요청
                Map<String, Object> validationResult = authServiceClient.validateToken(bearerToken);

                if (!(boolean) validationResult.get("valid")) {
                    httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    httpResponse.getWriter().write("{\"error\":\"유효하지 않은 토큰입니다.\"}");
                    return;
                }

                Long userNo = ((Number) validationResult.get("userNo")).longValue();
                log.info("JWT 토큰에서 추출한 사용자 번호: {}", userNo);

                // 사용 가능 여부 체크
                Map<String, Object> usageResponse = authServiceClient.checkUsageLimit(userNo);
                boolean canUse = (boolean) usageResponse.get("canUse");

                log.info("사용자 {}의 사용 가능 여부: {}", userNo, canUse);

                if (!canUse) {
                    httpResponse.setStatus(429);
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    httpResponse.getWriter().write("{\"error\":\"일일 사용량을 초과했습니다.\",\"remaining\":0}");
                    return;
                }

                // 응답 캐싱 래퍼
                ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

                // 요청 처리
                chain.doFilter(request, responseWrapper);

                // 응답 상태 코드 확인
                int statusCode = responseWrapper.getStatus();
                log.info("응답 상태 코드: {}", statusCode);

                // 응답이 성공일 때만 사용량 증가
                if (statusCode == HttpServletResponse.SC_OK) {
                    try {
                        Map<String, Object> incrementResult = authServiceClient.incrementUsage(userNo);
                        log.info("사용량 증가 결과: {}", incrementResult);
                        log.info("사용자 {}의 사용량이 증가되었습니다. 엔드포인트: {}", userNo, httpRequest.getRequestURI());
                    } catch (Exception e) {
                        log.error("사용량 증가 실패 - 사용자: {}", userNo, e);
                    }
                }

                // 캐시된 응답 전송
                responseWrapper.copyBodyToResponse();
                return;

            } catch (Exception e) {
                log.error("인증 처리 중 오류 발생: ", e);
                httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write("{\"error\":\"인증 처리 중 오류가 발생했습니다.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean shouldCheckUsage(String uri, String method) {
        // POST /api/v1/schedules/optimize-1 엔드포인트만 체크
        return "POST".equalsIgnoreCase(method) && uri.equals("/api/v1/schedules/optimize-1");
    }
}