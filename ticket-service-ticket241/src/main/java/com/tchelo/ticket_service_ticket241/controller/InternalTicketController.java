package com.tchelo.ticket_service_ticket241.controller;

import com.tchelo.ticket_service_ticket241.dto.response.TicketPdfDto;
import com.tchelo.ticket_service_ticket241.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoints service-à-service (clé X-Internal-Api-Key exigée par InternalApiKeyFilter).
// Non routés par la gateway : appelables uniquement par les autres microservices via Eureka.
@RestController
@RequestMapping("/internal/tickets")
@RequiredArgsConstructor
@Slf4j
public class InternalTicketController {

    private final TicketService ticketService;

    // PDF des billets payés d'une commande, joint à l'email de confirmation par notification-service
    @GetMapping(value = "/orders/{orderId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getOrderTicketsPdf(@PathVariable String orderId) {
        log.info("Demande interne du PDF des tickets de la commande {}", orderId);

        TicketPdfDto pdf = ticketService.getOrderTicketsPdfForConfirmation(orderId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.getContent());
    }
}
