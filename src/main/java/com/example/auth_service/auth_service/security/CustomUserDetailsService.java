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
            // ✅ 소셜 로그인 사용자의 경우 (loginType = 1)
            log.debug("소셜 로그인 사용자: {}", email);
            // 소셜 로그인 사용자는 임시 비밀번호 생성 (실제로는 사용되지 않음)
            password = "{noop}SOCIAL_LOGIN_NO_PASSWORD"; // {noop}은 암호화 없음을 의미
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
                password, // 소셜 로그인의 경우 임시 비밀번호, 일반 로그인의 경우 실제 비밀번호
                true, // enabled
                true, // accountNonExpired  
                true, // credentialsNonExpired
                true, // accountNonLocked
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    /**
     * ✅ JWT 토큰 검증용 특별 메서드 (비밀번호 검증 없음)
     * JWT 필터에서 토큰이 이미 검증된 사용자를 위한 UserDetails 생성
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserForJwtToken(String email) throws UsernameNotFoundException {
        log.debug("JWT 토큰 기반 사용자 로드: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email : " + email));

        // 사용자가 탈퇴한 경우 차단
        if ("WITHDRAWN".equals(user.getStatus())) {
            throw new UsernameNotFoundException("탈퇴한 회원입니다.");
        }

        log.debug("JWT 사용자 로드 성공: {} (로그인 타입: {})", email, 
                  user.getLoginType() == 1 ? "소셜" : "일반");

        // JWT 토큰 검증이 이미 완료된 상태이므로 비밀번호는 중요하지 않음
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                "{noop}JWT_VERIFIED", // JWT 검증 완료를 나타내는 임시 비밀번호
                true, // enabled
                true, // accountNonExpired  
                true, // credentialsNonExpired
                true, // accountNonLocked
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}