package com.example.auth_service.security;

import com.example.auth_service.domain.User;
import com.example.common.dto.domain.auth.AuthProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private Long id;
    private String email;
    @JsonIgnore
    private String password;
    private String name;
    private String phoneNumber;
    private AuthProvider provider;
    private Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;

    public static UserPrincipal create(User user) {
        // 단순화된 권한 설정 (모든 사용자에게 ROLE_USER 부여)
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER"));

        return new UserPrincipal(
                user.getUserNo(), // getId() -> getUserNo()
                user.getEmail(),
                null, // password는 다른 테이블에 있으므로 null
                user.getUsername(), // getName() -> getUserName()
                null, // phoneNumber 제거
                null, // provider 제거
                authorities,
                null);
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        userPrincipal.attributes = attributes;
        return userPrincipal;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}