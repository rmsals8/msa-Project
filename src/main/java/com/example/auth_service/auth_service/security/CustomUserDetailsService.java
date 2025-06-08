package com.example.auth_service.auth_service.security;

import com.example.auth_service.auth_service.domain.User;
import com.example.auth_service.auth_service.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("로그인 시도: {}", email);

        // ✅ 한 번의 쿼리로 User, Password, UserAgreement 함께 조회
        User user = userRepository.findByEmailWithDetails(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email : " + email));

        // 사용자가 탈퇴한 경우 로그인 차단
        if ("WITHDRAWN".equals(user.getStatus())) {
            throw new UsernameNotFoundException("탈퇴한 회원입니다.");
        }

        // ✅ 소셜 로그인 사용자와 일반 사용자를 구분해서 처리
        String password;
        
        if (user.getLoginType() != null && user.getLoginType() == 1) {
            // 소셜 로그인 사용자의 경우 (loginType = 1)
            log.debug("소셜 로그인 사용자: {}", email);
            password = ""; // 빈 비밀번호 사용 (소셜 로그인은 비밀번호가 필요 없음)
        } else {
            // 일반 로그인 사용자의 경우 (loginType = 0 또는 null)
            if (!user.hasPassword()) {
                log.warn("일반 사용자 {}의 비밀번호가 설정되지 않았습니다.", email);
                throw new UsernameNotFoundException("Password not found for user : " + email);
            }
            password = user.getPassword();
        }

        log.debug("로그인 성공: {} (로그인 타입: {})", email, 
                  user.getLoginType() == 1 ? "소셜" : "일반");

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password, // 소셜 로그인의 경우 빈 문자열, 일반 로그인의 경우 실제 비밀번호
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}