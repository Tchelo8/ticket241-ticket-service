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
public class PaymentFailedEvent {

    private String paymentId;
    private String orderId;
    private List<Long> ticketIds;
    private String userId;
    private String failureReason;
    private String errorCode;
    private LocalDateTime failedAt;

    // Métadonnées
    private String kafkaEventId;
    private LocalDateTime timestamp;
}
