package com.tchelo.ticket_service_ticket241.client.dto;

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
public class EventDetailsDto {

    private Long id;
    private String uuid;
    private String name;
    private String slug;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String venueName;
    private Long cityId;
    private String cityName;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer maxTicketsPerUser;
    // null si la vente est ouverte, sinon la raison donnée par event-service (brouillon, annulé, passé…)
    private String saleClosedReason;
    private List<TicketCategoryDetailsDto> ticketCategories;
}
