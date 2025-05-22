package com.example.auth_service.auth_service.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawRequest {
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}