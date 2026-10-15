package com.tchelo.ticket_service_ticket241.client;

import com.tchelo.ticket_service_ticket241.client.dto.EventDetailsDto;
import com.tchelo.ticket_service_ticket241.client.dto.TicketCategoryDetailsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// Lecture seule : les places ne se modifient que par Kafka (ticket-reserved, ticket-paid…).
// Les erreurs (404, service injoignable) remontent en FeignException et sont traduites en
// réponses HTTP claires par GlobalExceptionHandler.
@FeignClient(name = "event-service-ticket241")
public interface EventServiceClient {

    // Récupère les détails d'un événement
    @GetMapping("/api/events/{uuid}")
    EventDetailsDto getEventByUuid(@PathVariable("uuid") String uuid);

    // Récupère les détails d'un événement par ID
    @GetMapping("/api/events/id/{id}")
    EventDetailsDto getEventById(@PathVariable("id") Long id);

    // Récupère une catégorie de ticket (404 si elle n'appartient pas à l'événement)
    @GetMapping("/api/events/{eventId}/categories/{categoryId}")
    TicketCategoryDetailsDto getTicketCategory(
            @PathVariable("eventId") Long eventId,
            @PathVariable("categoryId") Long categoryId
    );

    // Vérifie la disponibilité des places (indicatif : la réservation fait foi côté event-service)
    @GetMapping("/api/events/{eventId}/categories/{categoryId}/availability")
    Boolean checkAvailability(
            @PathVariable("eventId") Long eventId,
            @PathVariable("categoryId") Long categoryId,
            @RequestParam("quantity") Integer quantity
    );
}
