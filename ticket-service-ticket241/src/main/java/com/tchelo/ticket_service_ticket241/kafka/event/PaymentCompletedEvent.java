package com.tchelo.ticket_service_ticket241.kafka.event;

import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
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
public class PaymentCompletedEvent {

    private String paymentId;
    private String orderId;
    private List<Long> ticketIds;
    private String userId;
    private PaymentMethod paymentMethod;
    private String paymentProvider;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime completedAt;

    // Métadonnées
    private String kafkaEventId;
    private LocalDateTime timestamp;
}
