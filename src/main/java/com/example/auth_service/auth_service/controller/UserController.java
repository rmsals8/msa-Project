package com.example.auth_service.auth_service.controller;

import com.example.auth_service.auth_service.domain.User;

import com.example.auth_service.auth_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            log.info("현재 인증된 사용자 정보 요청");

            if (userDetails == null) {
                log.warn("인증된 사용자가 null입니다");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증 정보가 유효하지 않습니다."));
            }

            log.info("사용자 이메일: {}", userDetails.getUsername());

            // 이메일로 사용자 정보 조회
            User user = userService.getUserByEmail(userDetails.getUsername());

            log.info("조회된 사용자 - 이름: {}, 이메일: {}", user.getUsername(), user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getUserNo());
            response.put("name", user.getUsername());
            response.put("email", user.getEmail());
            response.put("loginType", user.getLoginType());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("사용자 정보 조회 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "사용자 정보를 조회할 수 없습니다."));
        }
    }
}