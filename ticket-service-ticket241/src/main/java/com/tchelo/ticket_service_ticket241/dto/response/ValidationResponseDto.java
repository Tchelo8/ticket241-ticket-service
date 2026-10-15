package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResponseDto {

    private Boolean isValid;
    private String message;
    private String validationResult;

    // Informations du ticket s'il est  valide)
    private String ticketNumber;
    private String eventName;
    private LocalDateTime eventStartDate;
    private String ticketCategoryName;
    private String seatNumber;
    private String userName;

    // Validation
    private LocalDateTime validatedAt;
    private String validatedBy;
    private String validationLocation;

    // Alertes concernant le ticket
    private Boolean alreadyUsed;
    private Boolean isExpired;
    private Boolean isSuspicious;
    private LocalDateTime previousValidationAt;
}
