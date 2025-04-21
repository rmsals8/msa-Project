package com.example.auth_service.domain;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "social_logins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLogin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_login_id")
    private Long socialLoginId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_no", referencedColumnName = "user_no")
    private User user;
    
    @Column(name = "social_code")
    private Integer socialCode;
    
    @Column(name = "external_id")
    private String externalId;
    
    @Column(name = "access_token")
    private String accessToken;
    
    @Column(name = "update_date")
    private LocalDateTime updateDate;
}