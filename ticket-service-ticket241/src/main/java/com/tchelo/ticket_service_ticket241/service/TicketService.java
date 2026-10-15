package com.tchelo.ticket_service_ticket241.service;

import com.tchelo.ticket_service_ticket241.dto.request.*;
import com.tchelo.ticket_service_ticket241.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TicketService {

    // Crée les billets (PENDING) et demande les places à event-service. Ils passent RESERVED
    // (réservés ticket.reservation.hold-minutes) dès que event-service confirme.
    ReservationResponseDto reserveTickets(ReserveTicketsDto dto, String userId);

    // Lance le paiement de billets RESERVED
    PurchaseResponseDto purchaseTickets(PurchaseTicketsDto dto, String userId);

    // Consultation d'un billet (et donc de son QR code) : propriétaire ou administrateur uniquement
    TicketResponseDto getTicketByUuid(String uuid, String userId, String roles);

    TicketResponseDto getTicketByNumber(String ticketNumber, String userId, String roles);

    // Liste les tickets d'un utilisateur
    Page<TicketSummaryDto> getMyTickets(String userId, Pageable pageable);

    // Liste les tickets à venir d'un utilisateur
    Page<TicketSummaryDto> getMyUpcomingTickets(String userId, Pageable pageable);

    // Liste tous les tickets d'un événement
    Page<TicketSummaryDto> getTicketsByEvent(Long eventId, Pageable pageable);

    // Billets d'une commande : propriétaire ou administrateur uniquement
    List<TicketResponseDto> getTicketsByOrder(String orderId, String userId, String roles);

    // Annule un ticket
    TicketResponseDto cancelTicket(String uuid, CancelTicketDto dto, String userId);

    // Valide un ticket à l'entrée via QR Code
    ValidationResponseDto validateTicket(ValidateTicketDto dto);

    // Transfère un ticket à un autre utilisateur
    TicketResponseDto transferTicket(String uuid, TransferTicketDto dto, String userId);

    // Récupère le QR Code d'un ticket
    QRCodeResponseDto getTicketQRCode(String uuid, String userId);

    // PDF d'un billet payé (propriétaire ou administrateur)
    TicketPdfDto downloadTicket(String uuid, String userId, String roles);

    // PDF des billets payés d'une commande (ceux de l'utilisateur, ou tous pour un administrateur)
    TicketPdfDto downloadOrderTickets(String orderId, String userId, String roles);

    // PDF de tous les billets payés d'une commande, pour l'email de confirmation (appel interne
    // de notification-service, protégé par la clé interne)
    TicketPdfDto getOrderTicketsPdfForConfirmation(String orderId);

    // Récupère les statistiques d'un événement
    TicketStatisticsDto getEventStatistics(Long eventId);
}
