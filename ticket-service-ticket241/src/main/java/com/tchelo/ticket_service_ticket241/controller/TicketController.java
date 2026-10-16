package com.tchelo.ticket_service_ticket241.controller;

import com.tchelo.ticket_service_ticket241.dto.request.*;
import com.tchelo.ticket_service_ticket241.dto.response.*;
import com.tchelo.ticket_service_ticket241.security.UserContext;
import com.tchelo.ticket_service_ticket241.service.GuestTicketService;
import com.tchelo.ticket_service_ticket241.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketController {

    private final TicketService ticketService;
    private final GuestTicketService guestTicketService;


    // ENDPOINTS PUBLICS / UTILISATEUR AUTHENTIFIÉ

    //Réserver des tickets (10 minutes)
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponseDto> reserveTickets(
            @Valid @RequestBody ReserveTicketsDto dto,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String accountEmail
    ) {
        log.info("Requête réservation tickets : {} tickets pour event {}",
                dto.getQuantity(), dto.getEventId());

        // Email du compte (injecté par la gateway depuis le JWT) si le front n'en fournit pas :
        // c'est l'adresse qui recevra l'email de confirmation avec les billets
        if ((dto.getUserEmail() == null || dto.getUserEmail().isBlank()) && accountEmail != null) {
            dto.setUserEmail(accountEmail);
        }

        ReservationResponseDto response = ticketService.reserveTickets(dto, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Acheter/Confirmer des tickets réservés
    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponseDto> purchaseTickets(
            @Valid @RequestBody PurchaseTicketsDto dto,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Requête achat tickets : {} tickets", dto.getTicketIds().size());

        PurchaseResponseDto response = ticketService.purchaseTickets(dto, userId);

        return ResponseEntity.ok(response);
    }

    // Récupérer un ticket par UUID (propriétaire ou admin : la réponse contient le QR code)
    @GetMapping("/{uuid}")
    public ResponseEntity<TicketResponseDto> getTicketByUuid(
            @PathVariable String uuid,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        log.info("Requête récupération ticket : {}", uuid);

        TicketResponseDto response = ticketService.getTicketByUuid(uuid, userId, roles);

        return ResponseEntity.ok(response);
    }

    // Récupérer un ticket par numéro (propriétaire ou admin)
    @GetMapping("/number/{ticketNumber}")
    public ResponseEntity<TicketResponseDto> getTicketByNumber(
            @PathVariable String ticketNumber,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        log.info("Requête récupération ticket par numéro : {}", ticketNumber);

        TicketResponseDto response = ticketService.getTicketByNumber(ticketNumber, userId, roles);

        return ResponseEntity.ok(response);
    }

    //Lister mes tickets
    @GetMapping("/my-tickets")
    public ResponseEntity<Page<TicketSummaryDto>> getMyTickets(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Requête liste tickets pour user : {}", userId);

        Sort sort = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TicketSummaryDto> response = ticketService.getMyTickets(userId, pageable);

        return ResponseEntity.ok(response);
    }

    //Lister mes tickets à venir
    @GetMapping("/my-upcoming-tickets")
    public ResponseEntity<Page<TicketSummaryDto>> getMyUpcomingTickets(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Requête liste tickets à venir pour user : {}", userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("eventStartDate").ascending());

        Page<TicketSummaryDto> response = ticketService.getMyUpcomingTickets(userId, pageable);

        return ResponseEntity.ok(response);
    }

    // Récupérer les tickets d'une commande (sert aussi à suivre le passage PENDING → RESERVED après /reserve).
    // Un utilisateur ne voit que ses propres billets de la commande, un admin les voit tous.
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<TicketResponseDto>> getTicketsByOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        log.info("Requête tickets pour commande : {}", orderId);

        List<TicketResponseDto> response = ticketService.getTicketsByOrder(orderId, userId, roles);

        return ResponseEntity.ok(response);
    }

    // Rattacher à son compte une commande passée sans compte : il faut le jeton de la commande
    // (le lien reçu à l'achat), pas seulement le même numéro, qui n'est pas vérifié à l'inscription
    @PostMapping("/order/{orderId}/claim-guest")
    public ResponseEntity<List<TicketResponseDto>> claimGuestOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Guest-Token", required = false) String accessToken
    ) {
        log.info("Rattachement de la commande invité {} au compte {}", orderId, userId);

        return ResponseEntity.ok(guestTicketService.claimOrder(orderId, accessToken, userId));
    }

    // Annuler un ticket
    @PostMapping("/{uuid}/cancel")
    public ResponseEntity<TicketResponseDto> cancelTicket(
            @PathVariable String uuid,
            @Valid @RequestBody CancelTicketDto dto,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Requête annulation ticket : {} par {}", uuid, userId);

        TicketResponseDto response = ticketService.cancelTicket(uuid, dto, userId);

        return ResponseEntity.ok(response);
    }

    // Transférer un ticket à un autre utilisateur
    @PostMapping("/{uuid}/transfer")
    public ResponseEntity<TicketResponseDto> transferTicket(
            @PathVariable String uuid,
            @Valid @RequestBody TransferTicketDto dto,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Requête transfert ticket : {} par {}", uuid, userId);

        TicketResponseDto response = ticketService.transferTicket(uuid, dto, userId);

        return ResponseEntity.ok(response);
    }

    //Récupérer le QR Code d'un ticket
    @GetMapping("/{uuid}/qrcode")
    public ResponseEntity<QRCodeResponseDto> getTicketQRCode(
            @PathVariable String uuid,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Requête QR Code pour ticket : {} par {}", uuid, userId);

        QRCodeResponseDto response = ticketService.getTicketQRCode(uuid, userId);

        return ResponseEntity.ok(response);
    }

    // Télécharger le PDF d'un billet payé
    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> downloadTicket(
            @PathVariable String uuid,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        log.info("Téléchargement du ticket {} par {}", uuid, userId);

        return pdfResponse(ticketService.downloadTicket(uuid, userId, roles));
    }

    // Télécharger en un seul PDF tous les billets payés d'une commande
    @GetMapping("/order/{orderId}/download")
    public ResponseEntity<byte[]> downloadOrderTickets(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        log.info("Téléchargement des tickets de la commande {} par {}", orderId, userId);

        return pdfResponse(ticketService.downloadOrderTickets(orderId, userId, roles));
    }

    private ResponseEntity<byte[]> pdfResponse(TicketPdfDto pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.getFileName()).build().toString())
                .body(pdf.getContent());
    }


    // Valider un ticket à l'entrée (QR Code scanning)
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponseDto> validateTicket(
            @Valid @RequestBody ValidateTicketDto dto,
            @RequestHeader("X-User-Id") String validatorId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader(value = "X-User-Structure-Id", required = false) String structureId
    ) {
        log.info("Requête validation ticket par {}", validatorId);

        // Le validateur est toujours la personne connectée, jamais une valeur envoyée par le client
        dto.setValidatedBy(validatorId);

        // Droits (admin, ou agent / admin de la structure organisatrice) vérifiés dans le service
        ValidationResponseDto response = ticketService.validateTicket(dto, UserContext.of(validatorId, roles, structureId));

        return ResponseEntity.ok(response);
    }

    //Lister tous les tickets d'un événement (admin, ou admin de la structure organisatrice)
    @GetMapping("/event/{eventId}")
    public ResponseEntity<Page<TicketSummaryDto>> getTicketsByEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader(value = "X-User-Structure-Id", required = false) String structureId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Requête liste tickets pour événement : {}", eventId);

        Sort sort = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TicketSummaryDto> response = ticketService.getTicketsByEvent(
                eventId, pageable, UserContext.of(userId, roles, structureId));

        return ResponseEntity.ok(response);
    }

    //Récupérer les statistiques d'un événement (admin, ou admin de la structure organisatrice)
    @GetMapping("/event/{eventId}/statistics")
    public ResponseEntity<TicketStatisticsDto> getEventStatistics(
            @PathVariable Long eventId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader(value = "X-User-Structure-Id", required = false) String structureId
    ) {
        log.info("Requête statistiques pour événement : {}", eventId);

        TicketStatisticsDto response = ticketService.getEventStatistics(eventId, UserContext.of(userId, roles, structureId));

        return ResponseEntity.ok(response);
    }


    // Vérifier la santé du service
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Ticket Service est bien en marche");
    }
}