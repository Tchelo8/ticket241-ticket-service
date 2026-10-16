package com.tchelo.ticket_service_ticket241.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Session courte ouverte après un code valide : donne accès aux billets invités payés d'un numéro.
// Seul le hash du jeton est stocké.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "guest_access_sessions", indexes = {
        @Index(name = "idx_guest_session_token_hash", columnList = "token_hash", unique = true)
})
public class GuestAccessSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
