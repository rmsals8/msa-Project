package com.example.auth_service.auth_service.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationRequest {
    @NotBlank(message = "이메일은 필수 입력사항입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;
}