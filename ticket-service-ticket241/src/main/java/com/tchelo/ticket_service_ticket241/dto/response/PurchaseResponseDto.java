package com.tchelo.ticket_service_ticket241.dto.response;

import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
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
public class PurchaseResponseDto {

    private String orderId;
    private List<TicketResponseDto> tickets;
    private Integer ticketCount;
    private BigDecimal totalAmount;
    private String currency;

    // Paiement
    private String paymentId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String paymentReference;
    private LocalDateTime paymentCompletedAt;

    private String status;
    private String message;

    // Pour Mobile Money
    private String mobileMoneyAuthUrl;
    private String mobileMoneyTransactionId;
}
