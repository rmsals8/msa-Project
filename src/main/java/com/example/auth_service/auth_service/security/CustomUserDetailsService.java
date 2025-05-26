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

                // ✅ 비밀번호 존재 여부 확인
                if (!user.hasPassword()) {
                        log.warn("사용자 {}의 비밀번호가 설정되지 않았습니다.", email);
                        throw new UsernameNotFoundException("Password not found for user : " + email);
                }

                log.debug("로그인 성공: {}", email);

                return new org.springframework.security.core.userdetails.User(
                                user.getEmail(),
                                user.getPassword(), // ✅ N+1 문제 해결됨 (fetch join으로 이미 로드됨)
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        }
}
