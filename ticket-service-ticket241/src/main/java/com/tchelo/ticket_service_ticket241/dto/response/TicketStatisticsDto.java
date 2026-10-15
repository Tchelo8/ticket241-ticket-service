package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketStatisticsDto {

    private Long eventId;
    private String eventName;

    // Compteurs
    private Long totalTickets;
    private Long paidTickets;
    private Long reservedTickets;
    private Long cancelledTickets;
    private Long usedTickets;
    private Long expiredTickets;

    // Revenus
    private BigDecimal totalRevenue;
    private BigDecimal averageTicketPrice;

    // Check-in
    private Long checkedInCount;
    private Double checkInRate;

    // Tendances
    private Long ticketsLast24h;
    private Long ticketsLast7days;
    private Long ticketsLast30days;
}
