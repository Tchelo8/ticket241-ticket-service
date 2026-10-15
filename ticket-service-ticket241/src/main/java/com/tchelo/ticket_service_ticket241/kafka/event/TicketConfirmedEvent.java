package com.tchelo.ticket_service_ticket241.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Commande payée : notification-service envoie l'email de confirmation avec le PDF des billets.
// Contient tout ce qu'il faut pour rédiger l'email, sans rappeler ticket-service (seul le PDF y est demandé).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketConfirmedEvent {

    private String orderId;
    private String paymentId;
    private String userId;

    // Destinataire : email du compte (JWT) ou celui saisi à la réservation ; null → pas d'email possible
    private String recipientEmail;
    private String recipientFirstName;
    private String recipientLastName;

    private String eventName;
    private LocalDateTime eventStartDate;
    private String eventVenueName;
    private String eventVenueCityName;

    private Integer ticketCount;
    private BigDecimal totalAmount;
    private String currency;
    private List<TicketLine> tickets;

    private LocalDateTime confirmedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TicketLine {
        private String ticketNumber;
        private String categoryName;
        private String holderName;
        private BigDecimal finalPrice;
    }
}
