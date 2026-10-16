package com.tchelo.ticket_service_ticket241.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketReservedEvent {

    private String eventId;
    private Long eventIdLong;
    private String eventUuid;
    private Long ticketCategoryId;
    private Integer quantity;
    // Détail par catégorie (une commande peut en mélanger plusieurs). ticketCategoryId / quantity
    // ci-dessus ne sont renseignés que pour une commande d'une seule catégorie (anciens messages).
    private List<CategoryQuantity> lines;
    private List<Long> ticketIds;
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
    private LocalDateTime reservationDate;
    private LocalDateTime expirationDate;
    private String source;

    // Métadonnées pour Kafka
    private String kafkaEventId;
    private LocalDateTime timestamp;
}
