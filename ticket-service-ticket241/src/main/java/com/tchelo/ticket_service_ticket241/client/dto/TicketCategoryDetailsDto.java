package com.tchelo.ticket_service_ticket241.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCategoryDetailsDto {

    private Long id;
    private String uuid;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer reservedSeats;
    private Integer soldSeats;
    private Integer displayOrder;
    private Boolean isActive;
    private Integer maxTicketsPerOrder;
    private String colorCode;
}
