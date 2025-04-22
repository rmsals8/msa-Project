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

        // 사용량 체크가 필요한 엔드포인트인지 확인
        if (shouldCheckUsage(httpRequest.getRequestURI(), httpRequest.getMethod())) {
            Long userNo = extractUserNo(httpRequest);

            if (userNo != null) {
                // 먼저 사용 가능 여부만 확인
                try {
                    Map<String, Object> usageResponse = authServiceClient.checkUsageLimit(userNo);
                    boolean canUse = (boolean) usageResponse.get("canUse");

                    if (!canUse) {
                        httpResponse.setStatus(429); // HTTP 429 Too Many Requests
                        httpResponse.setContentType("application/json");
                        httpResponse.setCharacterEncoding("UTF-8");
                        httpResponse.getWriter().write("{\"error\":\"일일 사용량을 초과했습니다.\",\"remaining\":0}");
                        return;
                    }

                    // 응답을 캐싱하기 위한 wrapper 사용
                    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

                    // 요청 처리
                    chain.doFilter(request, responseWrapper);

                    // 응답 상태 코드가 200일 때만 사용량 증가
                    if (responseWrapper.getStatus() == HttpServletResponse.SC_OK) {
                        try {
                            authServiceClient.incrementUsage(userNo);
                            log.info("Usage incremented for user: {} after successful request to {}",
                                    userNo, httpRequest.getRequestURI());
                        } catch (Exception e) {
                            log.error("Error incrementing usage for user: {}", userNo, e);
                            // 사용량 증가 실패해도 응답은 정상적으로 보냄
                        }
                    }

                    // 캐시된 응답을 실제로 클라이언트에 전송
                    responseWrapper.copyBodyToResponse();
                    return;

                } catch (Exception e) {
                    log.error("Error checking usage limit: ", e);
                    httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "사용량 체크 중 오류가 발생했습니다.");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean shouldCheckUsage(String uri, String method) {
        // POST /api/v1/schedules/optimize-1 엔드포인트만 체크
        return "POST".equalsIgnoreCase(method) && uri.equals("/api/v1/schedules/optimize-1");
    }

    private Long extractUserNo(HttpServletRequest request) {
        // 실제 구현에서는 JWT 토큰에서 userNo를 추출해야 함
        // 여기서는 예시로 헤더에서 직접 가져옴
        String userNoHeader = request.getHeader("X-User-No");
        return userNoHeader != null ? Long.parseLong(userNoHeader) : null;
    }
}