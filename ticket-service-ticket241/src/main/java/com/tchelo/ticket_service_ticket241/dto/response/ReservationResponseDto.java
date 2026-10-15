package com.tchelo.ticket_service_ticket241.dto.response;


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
public class ReservationResponseDto {

    private String orderId;
    private List<TicketResponseDto> tickets;
    private Integer ticketCount;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime reservationDate;
    private LocalDateTime expirationDate;
    private Long minutesUntilExpiration;
    private String status;
    private String message;
}
