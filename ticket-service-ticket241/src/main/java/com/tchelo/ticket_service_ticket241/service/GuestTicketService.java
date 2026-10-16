package com.tchelo.ticket_service_ticket241.service;

import com.tchelo.ticket_service_ticket241.dto.request.GuestAccessRequestDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestAccessVerifyDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestPurchaseDto;
import com.tchelo.ticket_service_ticket241.dto.request.GuestReserveTicketsDto;
import com.tchelo.ticket_service_ticket241.dto.response.GuestAccessSessionDto;
import com.tchelo.ticket_service_ticket241.dto.response.GuestReservationResponseDto;
import com.tchelo.ticket_service_ticket241.dto.response.PurchaseResponseDto;
import com.tchelo.ticket_service_ticket241.dto.response.TicketPdfDto;
import com.tchelo.ticket_service_ticket241.dto.response.TicketResponseDto;

import java.util.List;
import java.util.Map;

// Achat sans compte. Deux façons d'accéder à ses billets :
// - le jeton de la commande (lien donné à la réservation), header X-Guest-Token
// - une session ouverte par un code reçu par email (lien perdu), header X-Guest-Session
// La réservation, le paiement et les PDF réutilisent TicketService : mêmes règles que pour un compte.
public interface GuestTicketService {

    GuestReservationResponseDto reserve(GuestReserveTicketsDto dto, String clientIp);

    List<TicketResponseDto> getOrder(String orderId, String accessToken);

    PurchaseResponseDto purchase(String orderId, String accessToken, GuestPurchaseDto dto);

    TicketPdfDto downloadOrder(String orderId, String accessToken);

    // Réponse identique que des billets existent ou non (ne révèle pas qui a acheté quoi)
    Map<String, String> requestAccessCode(GuestAccessRequestDto dto, String clientIp);

    GuestAccessSessionDto verifyAccessCode(GuestAccessVerifyDto dto, String clientIp);

    List<TicketResponseDto> getSessionTickets(String sessionToken);

    TicketPdfDto downloadSessionTicket(String sessionToken, String ticketUuid);

    // Un utilisateur connecté rattache à son compte une commande invité dont il a le lien
    List<TicketResponseDto> claimOrder(String orderId, String accessToken, String userId);
}
