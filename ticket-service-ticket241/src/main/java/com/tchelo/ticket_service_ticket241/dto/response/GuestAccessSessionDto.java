package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Jeton de session invité (header X-Guest-Session) : accès aux billets payés du numéro jusqu'à expiresAt
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestAccessSessionDto {

    private String sessionToken;
    private LocalDateTime expiresAt;
    private String message;
}
