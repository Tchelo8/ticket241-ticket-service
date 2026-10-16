package com.tchelo.ticket_service_ticket241.service.impl;

import com.tchelo.ticket_service_ticket241.client.NotificationServiceClient;
import com.tchelo.ticket_service_ticket241.client.dto.SimpleEmailRequestDto;
import com.tchelo.ticket_service_ticket241.dto.request.*;
import com.tchelo.ticket_service_ticket241.dto.response.*;
import com.tchelo.ticket_service_ticket241.entity.GuestAccessCode;
import com.tchelo.ticket_service_ticket241.entity.GuestAccessSession;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.exception.InvalidTicketOperationException;
import com.tchelo.ticket_service_ticket241.exception.TicketAccessDeniedException;
import com.tchelo.ticket_service_ticket241.mapper.TicketMapper;
import com.tchelo.ticket_service_ticket241.repository.GuestAccessCodeRepository;
import com.tchelo.ticket_service_ticket241.repository.GuestAccessSessionRepository;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import com.tchelo.ticket_service_ticket241.security.GuestRateLimiter;
import com.tchelo.ticket_service_ticket241.security.GuestTokens;
import com.tchelo.ticket_service_ticket241.service.GuestTicketService;
import com.tchelo.ticket_service_ticket241.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GuestTicketServiceImpl implements GuestTicketService {

    // Commandes invité non payées en même temps par numéro : au-delà, on bloquerait des places pour rien
    static final int MAX_OPEN_GUEST_ORDERS_PER_PHONE = 2;
    static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    static final Duration SESSION_VALIDITY = Duration.ofMinutes(30);

    private static final List<TicketStatus> OPEN_STATUSES = List.of(TicketStatus.PENDING, TicketStatus.RESERVED);
    private static final List<TicketStatus> PAID_STATUSES = List.of(TicketStatus.PAID, TicketStatus.USED);
    private static final Set<PaymentMethod> GUEST_PAYMENT_METHODS = Set.of(PaymentMethod.AIRTEL_MONEY, PaymentMethod.MOOV_MONEY);
    private static final String ACCESS_CODE_SENT_MESSAGE = "Si des billets payés existent pour ce numéro avec une "
            + "adresse email, un code valable 10 minutes vient d'y être envoyé.";

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final GuestAccessCodeRepository accessCodeRepository;
    private final GuestAccessSessionRepository accessSessionRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final GuestRateLimiter rateLimiter;

    // ===== Commande (jeton de la commande) =====

    @Override
    public GuestReservationResponseDto reserve(GuestReserveTicketsDto dto, String clientIp) {
        rateLimiter.check("reserve-ip:" + clientIp, 10, Duration.ofHours(1),
                "Trop de réservations depuis cette connexion, réessayez dans une heure");

        String phone = GuestTokens.normalizePhone(dto.getPhone());
        String guestUserId = GuestTokens.guestUserId(phone);

        if (ticketRepository.countOrdersByUserIdAndStatusIn(guestUserId, OPEN_STATUSES) >= MAX_OPEN_GUEST_ORDERS_PER_PHONE) {
            throw new InvalidTicketOperationException(
                    "Vous avez déjà des commandes en attente de paiement : payez-les ou attendez leur expiration");
        }

        // Même réservation qu'avec un compte (vente ouverte, places, limite par personne : ici par numéro)
        ReserveTicketsDto reservation = ReserveTicketsDto.builder()
                .eventId(dto.getEventId())
                .items(dto.resolvedItems())
                .userPhone(phone)
                .userEmail(dto.getEmail())
                .userFirstName(dto.getFirstName())
                .userLastName(dto.getLastName())
                .source(dto.getSource() != null ? dto.getSource() : "guest")
                .ipAddress(clientIp)
                .build();
        ReservationResponseDto response = ticketService.reserveTickets(reservation, guestUserId);

        String accessToken = GuestTokens.newToken();
        String tokenHash = GuestTokens.sha256(accessToken);
        for (Ticket ticket : ticketRepository.findByOrderIdAndIsArchivedFalse(response.getOrderId())) {
            ticket.setGuestOrder(true);
            ticket.setGuestAccessTokenHash(tokenHash);
        }
        log.info("Commande invité {} créée ({} billet(s))", response.getOrderId(), response.getTicketCount());

        return GuestReservationResponseDto.builder()
                .orderId(response.getOrderId())
                .accessToken(accessToken)
                .reservation(response)
                .message("Places en cours de réservation. Gardez le lien de votre commande : "
                        + "il donne accès à vos billets une fois le paiement validé.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDto> getOrder(String orderId, String accessToken) {
        return requireOrderAccess(orderId, accessToken).stream()
                .map(ticketMapper::toResponseDto)
                .toList();
    }

    @Override
    public PurchaseResponseDto purchase(String orderId, String accessToken, GuestPurchaseDto dto) {
        List<Ticket> tickets = requireOrderAccess(orderId, accessToken);

        if (!GUEST_PAYMENT_METHODS.contains(dto.getPaymentMethod())) {
            throw new InvalidTicketOperationException("Sans compte, le paiement se fait par Airtel Money ou Moov Money");
        }

        List<Long> payableIds = tickets.stream()
                .filter(ticket -> OPEN_STATUSES.contains(ticket.getStatus()))
                .map(Ticket::getId)
                .toList();
        if (payableIds.isEmpty()) {
            throw new InvalidTicketOperationException("Aucun billet à payer dans cette commande");
        }

        // Le paiement part toujours du numéro de la commande : c'est ce qui prouve qu'il appartient à l'invité
        // (un code de récupération donne ensuite accès aux billets de ce numéro)
        String phone = tickets.get(0).getUserPhone();
        PurchaseTicketsDto purchase = PurchaseTicketsDto.builder()
                .ticketIds(payableIds)
                .paymentMethod(dto.getPaymentMethod())
                .mobileMoneyNumber(phone)
                .build();
        return ticketService.purchaseTickets(purchase, tickets.get(0).getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPdfDto downloadOrder(String orderId, String accessToken) {
        List<Ticket> tickets = requireOrderAccess(orderId, accessToken);
        return ticketService.downloadOrderTickets(orderId, tickets.get(0).getUserId(), null);
    }

    @Override
    public List<TicketResponseDto> claimOrder(String orderId, String accessToken, String userId) {
        List<Ticket> tickets = requireOrderAccess(orderId, accessToken);

        for (Ticket ticket : tickets) {
            ticket.setTransferredFromUserId(ticket.getUserId());
            ticket.setUserId(userId);
            ticket.setGuestOrder(false);
            // Le lien invité ne fonctionne plus : les billets se consultent désormais depuis le compte
            ticket.setGuestAccessTokenHash(null);
            ticket.setStatusChangedAt(LocalDateTime.now());
            ticket.setStatusChangedBy(userId);
            ticket.setStatusChangeReason("Commande invité rattachée au compte");
            ticket.setUpdatedBy(userId);
        }
        log.info("Commande invité {} rattachée au compte {}", orderId, userId);

        return tickets.stream().map(ticketMapper::toResponseDto).toList();
    }

    // ===== Récupération par code (lien perdu) =====

    @Override
    public Map<String, String> requestAccessCode(GuestAccessRequestDto dto, String clientIp) {
        String phone = GuestTokens.normalizePhone(dto.getPhone());
        rateLimiter.check("code-ip:" + clientIp, 10, Duration.ofHours(1),
                "Trop de demandes depuis cette connexion, réessayez plus tard");
        rateLimiter.check("code-phone:" + phone, 3, Duration.ofMinutes(15),
                "Trop de codes demandés pour ce numéro, réessayez dans 15 minutes");

        // Seul l'email d'une commande PAYÉE est utilisé : le paiement est parti de ce numéro, donc son
        // propriétaire a choisi cette adresse (un inconnu ne peut pas y glisser la sienne sans payer)
        String email = ticketRepository.findByUserIdAndStatusInAndIsArchivedFalse(GuestTokens.guestUserId(phone), PAID_STATUSES)
                .stream()
                .filter(ticket -> ticket.getUserEmail() != null && !ticket.getUserEmail().isBlank())
                .max(Comparator.comparing(Ticket::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Ticket::getUserEmail)
                .orElse(null);

        if (email == null) {
            // Pas encore de SMS : sans email, le seul accès reste le lien de la commande
            log.info("Code invité demandé pour un numéro sans billet payé avec email");
            return Map.of("message", ACCESS_CODE_SENT_MESSAGE);
        }

        String code = GuestTokens.newNumericCode();
        LocalDateTime now = LocalDateTime.now();
        accessCodeRepository.save(GuestAccessCode.builder()
                .phone(phone)
                .codeHash(codeHash(phone, code))
                .expiresAt(now.plus(CODE_VALIDITY))
                .createdAt(now)
                .build());

        try {
            notificationServiceClient.sendEmail(new SimpleEmailRequestDto(
                    email,
                    "Votre code pour retrouver vos billets Ticket241",
                    "Bonjour,\n\nVotre code pour retrouver vos billets Ticket241 est : " + code + "\n\n"
                            + "Il est valable 10 minutes. Si vous n'êtes pas à l'origine de cette demande, "
                            + "ignorez ce message.\n\nL'équipe Ticket241"));
        } catch (Exception e) {
            log.error("Code invité non envoyé (notification-service) : {}", e.getMessage());
        }
        return Map.of("message", ACCESS_CODE_SENT_MESSAGE);
    }

    // Une tentative ratée doit rester comptée : pas de rollback sur le refus du code
    @Override
    @Transactional(noRollbackFor = InvalidTicketOperationException.class)
    public GuestAccessSessionDto verifyAccessCode(GuestAccessVerifyDto dto, String clientIp) {
        rateLimiter.check("verify-ip:" + clientIp, 20, Duration.ofHours(1),
                "Trop de tentatives depuis cette connexion, réessayez plus tard");

        String phone = GuestTokens.normalizePhone(dto.getPhone());
        LocalDateTime now = LocalDateTime.now();
        GuestAccessCode accessCode = accessCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .filter(code -> code.isUsable(now))
                .orElseThrow(() -> new InvalidTicketOperationException("Code invalide ou expiré, demandez-en un nouveau"));

        if (!GuestTokens.matches(phone + ":" + dto.getCode(), accessCode.getCodeHash())) {
            accessCode.setAttempts(accessCode.getAttempts() + 1);
            int remaining = GuestAccessCode.MAX_ATTEMPTS - accessCode.getAttempts();
            throw new InvalidTicketOperationException(remaining > 0
                    ? "Code incorrect (" + remaining + " tentative(s) restante(s))"
                    : "Code incorrect, demandez-en un nouveau");
        }
        accessCode.setUsed(true);

        String sessionToken = GuestTokens.newToken();
        LocalDateTime expiresAt = now.plus(SESSION_VALIDITY);
        accessSessionRepository.save(GuestAccessSession.builder()
                .phone(phone)
                .tokenHash(GuestTokens.sha256(sessionToken))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return GuestAccessSessionDto.builder()
                .sessionToken(sessionToken)
                .expiresAt(expiresAt)
                .message("Accès ouvert pendant 30 minutes")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDto> getSessionTickets(String sessionToken) {
        String guestUserId = GuestTokens.guestUserId(requireSessionPhone(sessionToken));
        return ticketRepository.findByUserIdAndStatusInAndIsArchivedFalse(guestUserId, PAID_STATUSES).stream()
                .map(ticketMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPdfDto downloadSessionTicket(String sessionToken, String ticketUuid) {
        // downloadTicket vérifie que le billet appartient à ce userId invité et qu'il est payé
        return ticketService.downloadTicket(ticketUuid, GuestTokens.guestUserId(requireSessionPhone(sessionToken)), null);
    }

    // Codes et sessions expirés : inutiles au-delà d'un jour (gardés un peu pour le diagnostic)
    @Scheduled(cron = "0 15 * * * *")
    public void purgeExpiredAccess() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        int codes = accessCodeRepository.deleteExpiredBefore(threshold);
        int sessions = accessSessionRepository.deleteExpiredBefore(threshold);
        if (codes + sessions > 0) {
            log.info("Accès invités expirés supprimés : {} code(s), {} session(s)", codes, sessions);
        }
    }

    // ===== Contrôles =====

    // Même réponse que la commande n'existe pas ou que le jeton soit faux : on ne confirme rien à un inconnu
    private List<Ticket> requireOrderAccess(String orderId, String accessToken) {
        List<Ticket> tickets = ticketRepository.findByOrderIdAndIsArchivedFalse(orderId);
        boolean allowed = !tickets.isEmpty() && tickets.stream().allMatch(ticket ->
                Boolean.TRUE.equals(ticket.getGuestOrder())
                        && GuestTokens.matches(accessToken, ticket.getGuestAccessTokenHash()));
        if (!allowed) {
            throw new TicketAccessDeniedException("Lien de commande invalide ou expiré");
        }
        return tickets;
    }

    private String requireSessionPhone(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new TicketAccessDeniedException("Session invalide ou expirée, demandez un nouveau code");
        }
        return accessSessionRepository.findByTokenHash(GuestTokens.sha256(sessionToken))
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(GuestAccessSession::getPhone)
                .filter(Objects::nonNull)
                .orElseThrow(() -> new TicketAccessDeniedException("Session invalide ou expirée, demandez un nouveau code"));
    }

    // Le numéro sert de sel : un même code n'a pas le même hash pour deux numéros
    private static String codeHash(String phone, String code) {
        return GuestTokens.sha256(phone + ":" + code);
    }
}
