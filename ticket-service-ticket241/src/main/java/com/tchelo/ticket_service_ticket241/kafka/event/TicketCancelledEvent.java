package com.tchelo.ticket_service_ticket241.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Billet annulé (par l'utilisateur ou après un paiement échoué). previousStatus indique à
// event-service quelle place libérer : RESERVED → une place réservée, PAID → une place vendue.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCancelledEvent {

    private String eventId;
    private Long ticketId;
    private String ticketNumber;
    private String orderId;
    private String previousStatus;
    private Long eventIdLong;
    private Long categoryId;
    private Integer quantity;
    private String userId;
    private String reason;
    private LocalDateTime cancelledAt;
    private LocalDateTime timestamp;
}
