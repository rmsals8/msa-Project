package com.example.auth_service.security;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.Password;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.PasswordRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordRepository passwordRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email : " + email));

        // 비밀번호 정보 조회
        Password passwordEntity = passwordRepository.findByUserNo(user.getUserNo())
                .orElseThrow(() -> new UsernameNotFoundException("Password not found for user : " + email));

        // 사용자 정보와 비밀번호 정보를 함께 전달
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                passwordEntity.getPassword(), // 암호화된 비밀번호
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}