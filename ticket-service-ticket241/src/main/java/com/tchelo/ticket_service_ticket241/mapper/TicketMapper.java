package com.tchelo.ticket_service_ticket241.mapper;

import com.tchelo.ticket_service_ticket241.dto.request.ReserveTicketsDto;
import com.tchelo.ticket_service_ticket241.dto.response.TicketResponseDto;
import com.tchelo.ticket_service_ticket241.dto.response.TicketSummaryDto;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class TicketMapper {

    // Convertit un Ticket en TicketResponseDto

    public TicketResponseDto toResponseDto(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        TicketResponseDto dto = new TicketResponseDto();
        BeanUtils.copyProperties(ticket, dto);

        // Champs calculés (non présents dans l'entité)
        dto.setIsExpired(ticket.isReservationExpired());
        dto.setCanBeCancelled(ticket.canBeCancelled());
        dto.setCanBeValidated(ticket.isValidForValidation());
        dto.setMinutesUntilExpiration(calculateMinutesUntilExpiration(ticket));

        return dto;
    }

    // Convertit un Ticket en TicketSummaryDto

    public TicketSummaryDto toSummaryDto(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        TicketSummaryDto dto = new TicketSummaryDto();

        BeanUtils.copyProperties(ticket, dto);

        // Champs calculés
        dto.setIsExpired(ticket.isReservationExpired());
        dto.setCanBeValidated(ticket.isValidForValidation());

        return dto;
    }

    // Crée un Ticket vierge à partir d'une réservation
    public Ticket createTicketFromReservation(
            ReserveTicketsDto dto,
            Long eventId,
            String eventUuid,
            String eventName,
            String eventSlug,
            LocalDateTime eventStartDate,
            LocalDateTime eventEndDate,
            String venueName,
            String cityName,
            Long categoryId,
            String categoryName,
            String categoryColor,
            BigDecimal categoryPrice,
            String userId
    ) {
        Ticket ticket = new Ticket();

        BeanUtils.copyProperties(dto, ticket, getNullPropertyNames(dto));

        // Événement (dénormalisé)
        ticket.setEventId(eventId);
        ticket.setEventUuid(eventUuid);
        ticket.setEventName(eventName);
        ticket.setEventSlug(eventSlug);
        ticket.setEventStartDate(eventStartDate);
        ticket.setEventEndDate(eventEndDate);
        ticket.setEventVenueName(venueName);
        ticket.setEventVenueCityName(cityName);


        ticket.setTicketCategoryId(categoryId);
        ticket.setTicketCategoryName(categoryName);
        ticket.setTicketCategoryColor(categoryColor);


        ticket.setUserId(userId);

        // Prix
        ticket.setBasePrice(categoryPrice);
        ticket.setServiceFee(calculateServiceFee(categoryPrice));
        ticket.setTaxAmount(calculateTax(categoryPrice));
        ticket.setDiscountAmount(BigDecimal.ZERO);
        ticket.setCurrency("XAF");


        ticket.setIsGift(dto.getIsGift() != null ? dto.getIsGift() : false);
        ticket.setStatus(TicketStatus.PENDING);
        ticket.calculateFinalPrice();

        return ticket;
    }

    // Met à jour un Ticket existant (ignore les propriétés null)
    public void updateTicket(Ticket ticket, Object updateDto) {
        if (ticket == null || updateDto == null) {
            return;
        }

        BeanUtils.copyProperties(updateDto, ticket, getNullPropertyNames(updateDto));
    }

    // Calcule les frais de service (5% du prix de base sur chaque ticket)
    private BigDecimal calculateServiceFee(BigDecimal basePrice) {
        if (basePrice == null) {
            return BigDecimal.ZERO;
        }
        return basePrice.multiply(new BigDecimal("0.05"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    //Calcule la TVA (20% du prix de base) à enlever peut être en prod car les taxs sont payées par les structures elles même
    private BigDecimal calculateTax(BigDecimal basePrice) {
        if (basePrice == null) {
            return BigDecimal.ZERO;
        }
        return basePrice.multiply(new BigDecimal("0.20"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // Calcule les minutes restantes avant expiration d'un ticket
    private Long calculateMinutesUntilExpiration(Ticket ticket) {
        if (ticket.getExpirationDate() == null || ticket.getStatus() != TicketStatus.RESERVED) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getExpirationDate())) {
            return 0L;
        }

        return Duration.between(now, ticket.getExpirationDate()).toMinutes();
    }

    // Retourne les noms des propriétés null (pour ignorer lors de la copie)
    private String[] getNullPropertyNames(Object source) {
        final org.springframework.beans.BeanWrapper src = new org.springframework.beans.BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        java.util.Set<String> emptyNames = new java.util.HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }

        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }
}