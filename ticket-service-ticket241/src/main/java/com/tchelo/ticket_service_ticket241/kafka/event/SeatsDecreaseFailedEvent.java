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
public class SeatsDecreaseFailedEvent {

    private Long eventId;
    private Long categoryId;
    private Integer quantity;
    private List<Long> ticketIds;
    private String orderId;
    private String failureReason;
    private LocalDateTime failedAt;

    // Métadonnées
    private String kafkaEventId;
    private LocalDateTime timestamp;
}
