package com.tchelo.ticket_service_ticket241.dto.response;

import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSummaryDto {

    private Long id;
    private String uuid;
    private String ticketNumber;

    // Événement (infos minimales comparé à celles que j'ai mis dans TicketResponseDto complet)
    private Long eventId;
    private String eventName;
    private LocalDateTime eventStartDate;
    private String eventVenueName;
    private String eventVenueCityName;

    // Catégorie
    private String ticketCategoryName;
    private String ticketCategoryColor;

    // Siège
    private String seatNumber;

    // Prix
    private BigDecimal finalPrice;
    private String currency;

    // Statut
    private TicketStatus status;

    // Dates
    private LocalDateTime purchaseDate;

    // Champs calculés
    private Boolean isExpired;
    private Boolean canBeValidated;
}
