package com.tchelo.ticket_service_ticket241.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Code à 6 chiffres envoyé à un invité pour retrouver ses billets (lien perdu). Seul le hash est stocké.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "guest_access_codes", indexes = {
        @Index(name = "idx_guest_code_phone", columnList = "phone")
})
public class GuestAccessCode {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isUsable(LocalDateTime now) {
        return !used && attempts < MAX_ATTEMPTS && expiresAt.isAfter(now);
    }
}
