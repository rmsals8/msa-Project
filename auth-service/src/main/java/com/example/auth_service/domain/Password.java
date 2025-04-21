package com.example.auth_service.domain;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "passwords")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Password {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_id")
    private Long passwordId;

    @Column(name = "user_no")
    private Long userNo;

    private String salt;
    private String password;

    @Column(name = "update_date")
    private LocalDateTime updateDate;
}