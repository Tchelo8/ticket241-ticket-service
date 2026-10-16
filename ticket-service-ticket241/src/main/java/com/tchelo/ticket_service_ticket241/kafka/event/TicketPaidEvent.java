package com.tchelo.ticket_service_ticket241.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// Paiement confirmé pour une commande : event-service passe ses places de "réservées" à "vendues"
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPaidEvent {

    private String orderId;
    private String paymentId;
    private Long eventId;
    private Long categoryId;
    private Integer quantity;
    // Détail par catégorie (une commande peut en mélanger plusieurs). ticketCategoryId / quantity
    // ci-dessus ne sont renseignés que pour une commande d'une seule catégorie (anciens messages).
    private List<CategoryQuantity> lines;
    private List<Long> ticketIds;
    private LocalDateTime paidAt;
    private LocalDateTime timestamp;
}
