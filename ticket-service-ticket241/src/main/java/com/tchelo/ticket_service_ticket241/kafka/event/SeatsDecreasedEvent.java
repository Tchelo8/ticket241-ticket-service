package com.tchelo.ticket_service_ticket241.kafka.event;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatsDecreasedEvent {

    private Long eventId;
    private String eventUuid;
    private Long categoryId;
    private Integer quantity;
    private List<Long> ticketIds;
    private String orderId;
    private Boolean success;
    private String message;
    private LocalDateTime processedAt;

    // Métadonnées
    private String kafkaEventId;
    private LocalDateTime timestamp;

    public static SeatsDecreasedEvent failed(TicketReservedEvent reservedEvent, String reason) {
        return SeatsDecreasedEvent.builder()
                .eventId(reservedEvent.getEventIdLong())
                .eventUuid(reservedEvent.getEventUuid())
                .categoryId(reservedEvent.getTicketCategoryId())
                .quantity(reservedEvent.getQuantity())
                .ticketIds(reservedEvent.getTicketIds())
                .orderId(reservedEvent.getOrderId())
                .success(false)
                .message(reason)
                .processedAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
