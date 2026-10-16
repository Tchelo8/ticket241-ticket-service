package com.tchelo.ticket_service_ticket241.controller;

import com.tchelo.ticket_service_ticket241.dto.request.GuestAccessRequestDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestAccessVerifyDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestPurchaseDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestReserveTicketsDto;
import com.tchelo.ticket_service_ticket241.dto.response.*;
import com.tchelo.ticket_service_ticket241.service.GuestTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Achat SANS compte (route publique de la gateway, headers d'identité supprimés).
// Les jetons passent en header, jamais dans l'URL de l'API (qui finit dans les logs) :
// - X-Guest-Token   : jeton de la commande, donné une seule fois à la réservation
// - X-Guest-Session : session ouverte par un code reçu par email (lien perdu)
@RestController
@RequestMapping("/api/tickets/guest")
@RequiredArgsConstructor
@Slf4j
public class GuestTicketController {

    static final String ORDER_TOKEN_HEADER = "X-Guest-Token";
    static final String SESSION_HEADER = "X-Guest-Session";

    private final GuestTicketService guestTicketService;

    @PostMapping("/reserve")
    public ResponseEntity<GuestReservationResponseDto> reserve(
            @Valid @RequestBody GuestReserveTicketsDto dto,
            HttpServletRequest request
    ) {
        log.info("Réservation invité : {} billet(s) pour l'événement {}", dto.getQuantity(), dto.getEventId());
        return ResponseEntity.status(HttpStatus.CREATED).body(guestTicketService.reserve(dto, clientIp(request)));
    }

    // Suivi de la commande (PENDING → RESERVED → PAID) et billets une fois payés
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<List<TicketResponseDto>> getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = ORDER_TOKEN_HEADER, required = false) String accessToken
    ) {
        return ResponseEntity.ok(guestTicketService.getOrder(orderId, accessToken));
    }

    @PostMapping("/orders/{orderId}/purchase")
    public ResponseEntity<PurchaseResponseDto> purchase(
            @PathVariable String orderId,
            @RequestHeader(value = ORDER_TOKEN_HEADER, required = false) String accessToken,
            @Valid @RequestBody GuestPurchaseDto dto
    ) {
        log.info("Paiement de la commande invité {}", orderId);
        return ResponseEntity.ok(guestTicketService.purchase(orderId, accessToken, dto));
    }

    @GetMapping("/orders/{orderId}/download")
    public ResponseEntity<byte[]> downloadOrder(
            @PathVariable String orderId,
            @RequestHeader(value = ORDER_TOKEN_HEADER, required = false) String accessToken
    ) {
        return pdfResponse(guestTicketService.downloadOrder(orderId, accessToken));
    }

    // Lien perdu : code envoyé à l'email d'une commande payée de ce numéro
    @PostMapping("/access/request")
    public ResponseEntity<Map<String, String>> requestAccessCode(
            @Valid @RequestBody GuestAccessRequestDto dto,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(guestTicketService.requestAccessCode(dto, clientIp(request)));
    }

    @PostMapping("/access/verify")
    public ResponseEntity<GuestAccessSessionDto> verifyAccessCode(
            @Valid @RequestBody GuestAccessVerifyDto dto,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(guestTicketService.verifyAccessCode(dto, clientIp(request)));
    }

    @GetMapping("/access/tickets")
    public ResponseEntity<List<TicketResponseDto>> getSessionTickets(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken
    ) {
        return ResponseEntity.ok(guestTicketService.getSessionTickets(sessionToken));
    }

    @GetMapping("/access/tickets/{uuid}/download")
    public ResponseEntity<byte[]> downloadSessionTicket(
            @PathVariable String uuid,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken
    ) {
        return pdfResponse(guestTicketService.downloadSessionTicket(sessionToken, uuid));
    }

    private ResponseEntity<byte[]> pdfResponse(TicketPdfDto pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.getFileName()).build().toString())
                .body(pdf.getContent());
    }

    // La gateway ajoute l'adresse du client EN FIN de X-Forwarded-For : les valeurs précédentes
    // viennent du client lui-même et ne sont pas fiables
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
