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

        // ✅ 수정된 부분: 소셜로그인과 일반로그인 구분 처리
        if (user.getLoginType() == 0) {
            // 일반 로그인 사용자 (loginType = 0): 반드시 비밀번호가 있어야 함
            if (!user.hasPassword()) {
                log.warn("일반 로그인 사용자 {}의 비밀번호가 설정되지 않았습니다.", email);
                throw new UsernameNotFoundException("Password not found for user : " + email);
            }
            log.debug("일반 로그인 성공: {}", email);
            
            return new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    user.getPassword(), // 실제 암호화된 비밀번호 사용
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                    
        } else if (user.getLoginType() == 1) {
            // 소셜 로그인 사용자 (loginType = 1): 비밀번호가 없어도 OK
            log.debug("소셜 로그인 성공: {}", email);
            
            return new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    "", // 소셜로그인은 빈 문자열 또는 임시 비밀번호 사용
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        } else {
            // 알 수 없는 로그인 타입
            log.warn("알 수 없는 로그인 타입: {} for user: {}", user.getLoginType(), email);
            throw new UsernameNotFoundException("Invalid login type for user : " + email);
        }
    }
}