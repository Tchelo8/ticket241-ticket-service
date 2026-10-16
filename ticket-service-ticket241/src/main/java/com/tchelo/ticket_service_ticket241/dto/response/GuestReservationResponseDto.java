package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Réservation invité : accessToken n'est renvoyé QU'ICI (seul son hash est stocké). Le front le garde
// et l'intègre au lien de la commande ; il est ensuite envoyé dans le header X-Guest-Token.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestReservationResponseDto {

    private String orderId;
    private String accessToken;
    private ReservationResponseDto reservation;
    private String message;
}
