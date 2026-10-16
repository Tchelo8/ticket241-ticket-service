package com.tchelo.ticket_service_ticket241.service.impl;

import com.tchelo.ticket_service_ticket241.client.EventServiceClient;
import com.tchelo.ticket_service_ticket241.client.dto.EventDetailsDto;
import com.tchelo.ticket_service_ticket241.client.dto.TicketCategoryDetailsDto;
import com.tchelo.ticket_service_ticket241.dto.request.*;
import com.tchelo.ticket_service_ticket241.dto.response.*;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.exception.*;
import com.tchelo.ticket_service_ticket241.kafka.event.CategoryQuantity;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketReservedEvent;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.kafka.producer.TicketEvents;
import com.tchelo.ticket_service_ticket241.mapper.TicketMapper;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import com.tchelo.ticket_service_ticket241.security.UserContext;
import com.tchelo.ticket_service_ticket241.service.QRCodeService;
import com.tchelo.ticket_service_ticket241.service.TicketPdfService;
import com.tchelo.ticket_service_ticket241.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketServiceImpl implements TicketService {

    // Billets qui comptent dans la limite maxTicketsPerUser d'un événement
    private static final List<TicketStatus> LIVE_STATUSES =
            List.of(TicketStatus.PENDING, TicketStatus.RESERVED, TicketStatus.PAID, TicketStatus.USED);

    // Seul un billet payé est un vrai billet d'entrée (USED : déjà scanné, gardé comme justificatif)
    private static final List<TicketStatus> DOWNLOADABLE_STATUSES = List.of(TicketStatus.PAID, TicketStatus.USED);

    private final TicketRepository ticketRepository;
    private final EventServiceClient eventServiceClient;
    private final TicketMapper ticketMapper;
    private final QRCodeService qrCodeService;
    private final KafkaProducerService kafkaProducerService;
    private final TicketPdfService ticketPdfService;

    @Value("${ticket.reservation.payment-window-minutes:15}")
    private long paymentWindowMinutes;


    // RÉSERVATION DE TICKETS
    // Les vérifications faites ici permettent de refuser tout de suite les cas évidents ; c'est
    // event-service qui tranche au final (places, vente ouverte) en traitant ticket-reserved.
    // Une erreur d'appel à event-service remonte en FeignException → 404/503 (GlobalExceptionHandler).
    @Override
    public ReservationResponseDto reserveTickets(ReserveTicketsDto dto, String userId) {

        List<ReservationItemDto> items = dto.resolvedItems();
        if (items.isEmpty()) {
            throw new InvalidTicketOperationException("Choisissez au moins une catégorie de billet");
        }
        int totalQuantity = ReservationItems.total(items);

        log.info("Réservation de {} tickets ({} catégorie(s)) pour l'événement {} par {}",
                totalQuantity, items.size(), dto.getEventId(), userId);

        EventDetailsDto eventDetails = eventServiceClient.getEventById(dto.getEventId());

        if (eventDetails.getSaleClosedReason() != null) {
            throw new InvalidTicketOperationException(eventDetails.getSaleClosedReason());
        }

        // Limite par personne : sur le total de la commande, toutes catégories confondues
        if (eventDetails.getMaxTicketsPerUser() != null) {
            long alreadyOwned = ticketRepository.countByUserIdAndEventIdAndStatusInAndIsArchivedFalse(
                    userId, dto.getEventId(), LIVE_STATUSES);
            if (alreadyOwned + totalQuantity > eventDetails.getMaxTicketsPerUser()) {
                throw new InvalidTicketOperationException(String.format(
                        "Limite de %d billet(s) par personne pour cet événement (vous en avez déjà %d)",
                        eventDetails.getMaxTicketsPerUser(), alreadyOwned));
            }
        }

        // Toutes les catégories sont vérifiées AVANT de créer le moindre billet
        Map<Long, TicketCategoryDetailsDto> categories = new LinkedHashMap<>();
        for (ReservationItemDto item : items) {
            // 404 si la catégorie n'appartient pas à cet événement
            TicketCategoryDetailsDto category = eventServiceClient.getTicketCategory(
                    dto.getEventId(), item.getTicketCategoryId());
            checkCategoryCanBeReserved(category, item.getQuantity());
            categories.put(item.getTicketCategoryId(), category);
        }

        // Une seule commande pour tout le panier : un seul paiement
        String orderId = UUID.randomUUID().toString();

        // Créer les tickets avec statut PENDING
        List<Ticket> tickets = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ReservationItemDto item : items) {
            TicketCategoryDetailsDto category = categories.get(item.getTicketCategoryId());

            for (int i = 0; i < item.getQuantity(); i++) {

                Ticket ticket = ticketMapper.createTicketFromReservation(
                        dto,
                        eventDetails.getId(),
                        eventDetails.getUuid(),
                        eventDetails.getName(),
                        eventDetails.getSlug(),
                        eventDetails.getStartDate(),
                        eventDetails.getEndDate(),
                        eventDetails.getVenueName(),
                        eventDetails.getCityName(),
                        category.getId(),
                        category.getName(),
                        category.getColorCode(),
                        category.getPrice(),
                        userId
                );

                ticket.setOrderId(orderId);
                ticket.setEventStructureId(eventDetails.getStructureId());
                ticket.setOrderTicketCount(totalQuantity);
                ticket.setIsPrimaryTicket(tickets.isEmpty());

                // UUID et numéro générés ici car le QR code en dépend (@PrePersist passerait trop tard)
                String ticketUuid = UUID.randomUUID().toString();
                String ticketNumber = Ticket.newTicketNumber();
                ticket.setUuid(ticketUuid);
                ticket.setTicketNumber(ticketNumber);

                // qrCode = contenu brut scanné à l'entrée, qrCodeImageUrl = image pour l'affichage
                assignNewQrCode(ticket);

                ticket.setStatus(TicketStatus.PENDING);
                ticket.setCreatedBy(userId);

                tickets.add(ticket);
                totalAmount = totalAmount.add(ticket.getFinalPrice());
            }
        }

        for (Ticket ticket : tickets) {
            ticket.setOrderTotalAmount(totalAmount);
        }

        // Sauvegarder les tickets
        List<Ticket> savedTickets = ticketRepository.saveAll(tickets);
        log.info("{} tickets créés avec statut PENDING", savedTickets.size());

        // Envoyé après le commit : event-service ne peut pas répondre avant que les billets existent.
        // event-service réserve les places de TOUTES les lignes, ou d'aucune.
        boolean singleCategory = items.size() == 1;
        TicketReservedEvent reservedEvent = TicketReservedEvent.builder()
                .eventId(eventDetails.getUuid())
                .eventIdLong(eventDetails.getId())
                .eventUuid(eventDetails.getUuid())
                .ticketCategoryId(singleCategory ? items.get(0).getTicketCategoryId() : null)
                .quantity(singleCategory ? totalQuantity : null)
                .lines(items.stream()
                        .map(item -> CategoryQuantity.builder()
                                .ticketCategoryId(item.getTicketCategoryId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .ticketIds(savedTickets.stream().map(Ticket::getId).collect(Collectors.toList()))
                .orderId(orderId)
                .userId(userId)
                .totalAmount(totalAmount)
                .reservationDate(LocalDateTime.now())
                .source(dto.getSource())
                .kafkaEventId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaProducerService.publishTicketReservedEvent(reservedEvent);

        return ReservationResponseDto.builder()
                .orderId(orderId)
                .tickets(savedTickets.stream()
                        .map(ticketMapper::toResponseDto)
                        .collect(Collectors.toList()))
                .ticketCount(savedTickets.size())
                .totalAmount(totalAmount)
                .currency("XAF")
                .reservationDate(LocalDateTime.now())
                .status("PENDING")
                .message("Réservation en cours de traitement. Les places seront confirmées sous peu : "
                        + "suivez la commande via /api/tickets/order/" + orderId)
                .build();
    }

    // Vérifications d'une ligne : catégorie en vente, maximum par commande, places annoncées
    private void checkCategoryCanBeReserved(TicketCategoryDetailsDto category, int quantity) {
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new InvalidTicketOperationException(
                    "La catégorie de billet " + category.getName() + " n'est plus en vente"
            );
        }

        if (category.getMaxTicketsPerOrder() != null && quantity > category.getMaxTicketsPerOrder()) {
            throw new InvalidTicketOperationException(String.format(
                    "Maximum %d billet(s) par commande dans la catégorie %s",
                    category.getMaxTicketsPerOrder(), category.getName()));
        }

        if (category.getAvailableSeats() == null || category.getAvailableSeats() < quantity) {
            throw new InsufficientSeatsException(
                    String.format("Plus assez de places disponibles. Catégorie: %s",
                            category.getName())
            );
        }
    }

    // ACHAT / CONFIRMATION DE TICKETS
    @Override
    public PurchaseResponseDto purchaseTickets(PurchaseTicketsDto dto, String userId) {

        log.info("Achat de {} tickets par {}", dto.getTicketIds().size(), userId);

        List<Ticket> tickets = ticketRepository.findAllById(dto.getTicketIds());

        if (tickets.isEmpty() || tickets.size() != dto.getTicketIds().stream().distinct().count()) {
            throw new TicketNotFoundException("Un ou plusieurs billets sont introuvables");
        }

        for (Ticket ticket : tickets) {
            if (!ticket.getUserId().equals(userId)) {
                throw new InvalidTicketOperationException(
                        "Vous ne pouvez acheter que vos propres tickets réservés"
                );
            }

            if (ticket.getStatus() == TicketStatus.PENDING) {
                throw new InvalidTicketOperationException(
                        "Les places de la commande sont en cours de confirmation, réessayez dans quelques secondes"
                );
            }

            if (ticket.getStatus() != TicketStatus.RESERVED) {
                throw new InvalidTicketOperationException(
                        String.format("Le ticket %s n'est pas disponible à l'achat (statut: %s)",
                                ticket.getTicketNumber(), ticket.getStatus())
                );
            }

            if (ticket.getPaymentStatus() == PaymentStatus.PROCESSING) {
                throw new InvalidTicketOperationException(
                        String.format("Un paiement est déjà en cours pour le ticket %s (paiement %s)",
                                ticket.getTicketNumber(), ticket.getPaymentId())
                );
            }

            if (ticket.isReservationExpired()) {
                throw new ReservationExpiredException(
                        String.format("La réservation du ticket %s a expiré",
                                ticket.getTicketNumber())
                );
            }
        }

        String orderId = tickets.get(0).getOrderId();
        boolean sameOrder = tickets.stream()
                .allMatch(t -> t.getOrderId().equals(orderId));

        if (!sameOrder) {
            throw new InvalidTicketOperationException(
                    "Tous les tickets doivent appartenir à la même commande"
            );
        }

        BigDecimal totalAmount = tickets.stream()
                .map(Ticket::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String paymentId = "PAY-" + UUID.randomUUID();

        // Le paiement mobile money peut prendre plusieurs minutes : on prolonge la réservation pour
        // que le job d'expiration ne libère pas les places pendant que l'utilisateur paie
        LocalDateTime paymentDeadline = LocalDateTime.now().plusMinutes(paymentWindowMinutes);
        for (Ticket ticket : tickets) {
            ticket.setPaymentId(paymentId);
            ticket.setPaymentMethod(dto.getPaymentMethod());
            ticket.setPaymentStatus(PaymentStatus.PROCESSING);
            ticket.setPaymentAmount(ticket.getFinalPrice());
            ticket.setPaymentCurrency("XAF");
            ticket.setExpirationDate(paymentDeadline);
            ticket.setUpdatedBy(userId);
        }

        log.info("Paiement {} lancé pour la commande {} (échéance {})", paymentId, orderId, paymentDeadline);

        kafkaProducerService.publishTicketPurchaseRequestedEvent(
                orderId, createPurchaseRequestedEvent(paymentId, orderId, tickets, dto, totalAmount));

        return PurchaseResponseDto.builder()
                .orderId(orderId)
                .tickets(tickets.stream()
                        .map(ticketMapper::toResponseDto)
                        .collect(Collectors.toList()))
                .ticketCount(tickets.size())
                .totalAmount(totalAmount)
                .currency("XAF")
                .paymentId(paymentId)
                .paymentMethod(dto.getPaymentMethod())
                .paymentStatus(PaymentStatus.PROCESSING)
                .paymentReference(dto.getPaymentReference())
                .status("PROCESSING")
                .message("Demande de paiement en cours de traitement. " +
                        "Vous recevrez une confirmation une fois le paiement validé.")
                .build();
    }

    private Map<String, Object> createPurchaseRequestedEvent(
            String paymentId,
            String orderId,
            List<Ticket> tickets,
            PurchaseTicketsDto dto,
            BigDecimal totalAmount
    ) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("paymentId", paymentId);
        event.put("orderId", orderId);
        event.put("ticketIds", tickets.stream().map(Ticket::getId).collect(Collectors.toList()));
        event.put("userId", tickets.get(0).getUserId());
        event.put("paymentMethod", dto.getPaymentMethod().name());
        event.put("amount", totalAmount);
        event.put("currency", "XAF");
        event.put("mobileMoneyNumber", dto.getMobileMoneyNumber());
        event.put("cardToken", dto.getCardToken());
        event.put("timestamp", LocalDateTime.now().toString());
        return event;
    }

    // ANNULATION DE TICKET
    @Override
    public TicketResponseDto cancelTicket(String uuid, CancelTicketDto dto, String userId) {

        log.info("Annulation du ticket {} par {}", uuid, userId);

        Ticket ticket = findTicketByUuid(uuid);

        if (!ticket.getUserId().equals(userId)) {
            throw new InvalidTicketOperationException(
                    "Vous ne pouvez annuler que vos propres tickets"
            );
        }

        // Annuler pendant le paiement ferait payer un billet dont la place est déjà rendue
        if (ticket.getStatus() == TicketStatus.RESERVED && ticket.getPaymentStatus() == PaymentStatus.PROCESSING) {
            throw new InvalidTicketOperationException(
                    "Un paiement est en cours pour ce ticket : attendez son résultat avant d'annuler"
            );
        }

        if (!ticket.canBeCancelled()) {
            throw new InvalidTicketOperationException(
                    String.format("Le ticket %s ne peut pas être annulé (statut: %s)",
                            ticket.getTicketNumber(), ticket.getStatus())
            );
        }

        TicketStatus previousStatus = ticket.getStatus();
        ticket.cancel(userId, dto.getReason());
        log.info("Ticket {} annulé avec succès (statut précédent : {})", ticket.getTicketNumber(), previousStatus);

        // event-service libère une place réservée ou vendue selon previousStatus
        kafkaProducerService.publishTicketCancelledEvent(TicketEvents.ticketCancelled(ticket, previousStatus));

        if (Boolean.TRUE.equals(dto.getRequestRefund()) && ticket.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.info("Demande de remboursement initiée pour ticket {}", ticket.getTicketNumber());
        }

        return ticketMapper.toResponseDto(ticket);
    }

    // CONSULTATION DE TICKETS
    @Override
    @Transactional(readOnly = true)
    public TicketResponseDto getTicketByUuid(String uuid, String userId, String roles) {
        Ticket ticket = findTicketByUuid(uuid);
        checkCanView(ticket, userId, roles);
        return ticketMapper.toResponseDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponseDto getTicketByNumber(String ticketNumber, String userId, String roles) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket non trouvé avec le numéro : " + ticketNumber
                ));
        checkCanView(ticket, userId, roles);
        return ticketMapper.toResponseDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDto> getMyTickets(String userId, Pageable pageable) {
        return ticketRepository.findByUserIdAndIsArchivedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(ticketMapper::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDto> getMyUpcomingTickets(String userId, Pageable pageable) {
        return ticketRepository.findUpcomingTicketsByUser(userId, LocalDateTime.now(), pageable)
                .map(ticketMapper::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDto> getTicketsByEvent(Long eventId, Pageable pageable, UserContext user) {
        requireEventManagement(eventServiceClient.getEventById(eventId), user);

        return ticketRepository.findByEventIdAndIsArchivedFalseOrderByCreatedAtDesc(eventId, pageable)
                .map(ticketMapper::toSummaryDto);
    }

    // Un administrateur voit toute la commande ; un utilisateur seulement ses billets de la commande
    // (un billet transféré appartient à quelqu'un d'autre)
    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDto> getTicketsByOrder(String orderId, String userId, String roles) {
        boolean admin = isAdmin(roles);
        return ticketRepository.findByOrderIdAndIsArchivedFalse(orderId).stream()
                .filter(ticket -> admin || ticket.getUserId().equals(userId))
                .map(ticketMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    // VALIDATION DE TICKET
    @Override
    public ValidationResponseDto validateTicket(ValidateTicketDto dto, UserContext user) {

        log.info("Validation du ticket avec QR Code par {}", user.userId());

        // Refus avant toute recherche : le scanner ne doit pas servir à sonder des billets
        if (!user.canScanTickets()) {
            throw new TicketAccessDeniedException("Seuls les contrôleurs d'entrée et les organisateurs peuvent scanner des billets");
        }

        if (!qrCodeService.validateQRCode(dto.getQrCode())) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("INVALID_FORMAT")
                    .message("Format de QR Code invalide")
                    .build();
        }

        // Recherche sur le contenu exact : un ancien QR code (avant transfert) ne correspond plus à rien
        Ticket ticket = ticketRepository.findByQrCode(dto.getQrCode())
                .orElse(null);

        if (ticket == null) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("NOT_FOUND")
                    .message("Ticket introuvable ou QR code périmé")
                    .build();
        }

        // Un agent ne scanne que les billets des événements de SA structure (sinon un agent de la
        // structure A pourrait faire entrer des billets d'un concert de la structure B)
        if (!user.canScanForStructure(resolveStructureId(ticket))) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("WRONG_ORGANIZER")
                    .message("Ce billet concerne un événement d'un autre organisateur")
                    .build();
        }

        if (ticket.getStatus() == TicketStatus.USED) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("ALREADY_USED")
                    .message("Ce ticket a déjà été utilisé")
                    .ticketNumber(ticket.getTicketNumber())
                    .eventName(ticket.getEventName())
                    .userName(ticket.getUserFirstName() + " " + ticket.getUserLastName())
                    .alreadyUsed(true)
                    .previousValidationAt(ticket.getValidatedAt())
                    .build();
        }

        if (ticket.getStatus() != TicketStatus.PAID) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("NOT_PAID")
                    .message(String.format("Ticket non payé (statut: %s)", ticket.getStatus()))
                    .ticketNumber(ticket.getTicketNumber())
                    .eventName(ticket.getEventName())
                    .userName(ticket.getUserFirstName() + " " + ticket.getUserLastName())
                    .build();
        }

        if (!ticket.isValidForValidation()) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("WRONG_DATE")
                    .message("Ce ticket n'est valide que le " +
                            ticket.getEventStartDate().toLocalDate())
                    .ticketNumber(ticket.getTicketNumber())
                    .eventName(ticket.getEventName())
                    .eventStartDate(ticket.getEventStartDate())
                    .build();
        }

        if (Boolean.TRUE.equals(ticket.getIsSuspicious())) {
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("SUSPICIOUS")
                    .message("Ticket signalé comme suspect. Contactez un superviseur.")
                    .ticketNumber(ticket.getTicketNumber())
                    .eventName(ticket.getEventName())
                    .isSuspicious(true)
                    .build();
        }

        try {
            ticket.validate(
                    dto.getValidatedBy(),
                    dto.getValidationLocation(),
                    dto.getValidationDevice()
            );

            Ticket validatedTicket = ticketRepository.save(ticket);

            log.info("Ticket {} validé avec succès", ticket.getTicketNumber());

            kafkaProducerService.publishTicketValidatedEvent(
                    validatedTicket.getOrderId(), createTicketValidatedEvent(validatedTicket, dto));

            return ValidationResponseDto.builder()
                    .isValid(true)
                    .validationResult("SUCCESS")
                    .message("Ticket validé avec succès")
                    .ticketNumber(validatedTicket.getTicketNumber())
                    .eventName(validatedTicket.getEventName())
                    .eventStartDate(validatedTicket.getEventStartDate())
                    .ticketCategoryName(validatedTicket.getTicketCategoryName())
                    .seatNumber(validatedTicket.getSeatNumber())
                    .userName(validatedTicket.getUserFirstName() + " " +
                            validatedTicket.getUserLastName())
                    .validatedAt(validatedTicket.getValidatedAt())
                    .validatedBy(validatedTicket.getValidatedBy())
                    .validationLocation(validatedTicket.getValidationLocation())
                    .alreadyUsed(false)
                    .isExpired(false)
                    .isSuspicious(false)
                    .build();

        } catch (IllegalStateException e) {
            log.error("Erreur lors de la validation : {}", e.getMessage());
            return ValidationResponseDto.builder()
                    .isValid(false)
                    .validationResult("ERROR")
                    .message(e.getMessage())
                    .build();
        }
    }

    private Map<String, Object> createTicketValidatedEvent(Ticket ticket, ValidateTicketDto dto) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("ticketId", ticket.getId());
        event.put("ticketNumber", ticket.getTicketNumber());
        event.put("orderId", ticket.getOrderId());
        event.put("eventIdLong", ticket.getEventId());
        event.put("eventName", ticket.getEventName());
        event.put("userId", ticket.getUserId());
        event.put("validatedBy", dto.getValidatedBy());
        event.put("validationLocation", dto.getValidationLocation());
        event.put("validationDevice", dto.getValidationDevice());
        event.put("validatedAt", ticket.getValidatedAt() != null ? ticket.getValidatedAt().toString() : null);
        event.put("timestamp", LocalDateTime.now().toString());
        return event;
    }

    // TRANSFERT DE TICKET
    @Override
    public TicketResponseDto transferTicket(String uuid, TransferTicketDto dto, String userId) {

        log.info("Transfert du ticket {} de {} vers {}", uuid, userId, dto.getNewUserId());

        Ticket ticket = findTicketByUuid(uuid);

        if (!ticket.getUserId().equals(userId)) {
            throw new InvalidTicketOperationException(
                    "Vous ne pouvez transférer que vos propres tickets"
            );
        }

        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new InvalidTicketOperationException(
                    "Seuls les tickets payés peuvent être transférés"
            );
        }

        if (dto.getNewUserId().equals(userId)) {
            throw new InvalidTicketOperationException("Ce ticket vous appartient déjà");
        }

        if (!LocalDateTime.now().isBefore(ticket.getEventStartDate())) {
            throw new InvalidTicketOperationException("L'événement a commencé : le ticket ne peut plus être transféré");
        }

        ticket.transfer(dto.getNewUserId(), dto.getTransferReason());

        // Le billet change de main : les coordonnées de l'ancien propriétaire ne doivent pas rester
        ticket.setUserEmail(dto.getNewUserEmail());
        ticket.setUserPhone(dto.getNewUserPhone());
        ticket.setUserFirstName(dto.getNewUserFirstName());
        ticket.setUserLastName(dto.getNewUserLastName());
        ticket.setBeneficiaryFirstName(null);
        ticket.setBeneficiaryLastName(null);
        ticket.setBeneficiaryEmail(null);
        ticket.setBeneficiaryPhone(null);

        // Nouveau QR code : celui que l'ancien propriétaire a pu garder (capture d'écran) ne passe plus
        assignNewQrCode(ticket);

        log.info("Ticket {} transféré avec succès à {}",
                ticket.getTicketNumber(), dto.getNewUserId());

        return ticketMapper.toResponseDto(ticket);
    }

    // QR CODE
    @Override
    @Transactional(readOnly = true)
    public QRCodeResponseDto getTicketQRCode(String uuid, String userId) {

        Ticket ticket = findTicketByUuid(uuid);

        if (!ticket.getUserId().equals(userId)) {
            throw new TicketAccessDeniedException(
                    "Vous ne pouvez accéder qu'au QR Code de vos propres tickets"
            );
        }

        return QRCodeResponseDto.builder()
                .ticketUuid(ticket.getUuid())
                .ticketNumber(ticket.getTicketNumber())
                .qrCode(ticket.getQrCode())
                .qrCodeImageUrl(ticket.getQrCodeImageUrl())
                .qrCodeBase64(ticket.getQrCodeImageUrl())
                .eventName(ticket.getEventName())
                .message("QR Code récupéré avec succès")
                .build();
    }

    // TÉLÉCHARGEMENT PDF
    @Override
    @Transactional(readOnly = true)
    public TicketPdfDto downloadTicket(String uuid, String userId, String roles) {

        Ticket ticket = findTicketByUuid(uuid);
        checkCanView(ticket, userId, roles);

        if (!DOWNLOADABLE_STATUSES.contains(ticket.getStatus())) {
            throw new InvalidTicketOperationException(
                    String.format("Seuls les tickets payés peuvent être téléchargés (statut: %s)", ticket.getStatus())
            );
        }

        return TicketPdfDto.builder()
                .fileName("billet-" + ticket.getTicketNumber() + ".pdf")
                .content(ticketPdfService.generateTicketsPdf(List.of(ticket)))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPdfDto downloadOrderTickets(String orderId, String userId, String roles) {

        boolean admin = isAdmin(roles);
        List<Ticket> tickets = ticketRepository.findByOrderIdAndIsArchivedFalse(orderId).stream()
                .filter(ticket -> admin || ticket.getUserId().equals(userId))
                .filter(ticket -> DOWNLOADABLE_STATUSES.contains(ticket.getStatus()))
                .collect(Collectors.toList());

        return buildOrderPdf(orderId, tickets);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPdfDto getOrderTicketsPdfForConfirmation(String orderId) {

        List<Ticket> tickets = ticketRepository.findByOrderIdAndIsArchivedFalse(orderId).stream()
                .filter(ticket -> DOWNLOADABLE_STATUSES.contains(ticket.getStatus()))
                .collect(Collectors.toList());

        return buildOrderPdf(orderId, tickets);
    }

    private TicketPdfDto buildOrderPdf(String orderId, List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            throw new TicketNotFoundException("Aucun ticket payé à télécharger pour la commande : " + orderId);
        }

        return TicketPdfDto.builder()
                .fileName("billets-ticket241-" + orderId.substring(0, Math.min(8, orderId.length())) + ".pdf")
                .content(ticketPdfService.generateTicketsPdf(tickets))
                .build();
    }

    // STATISTIQUES
    @Override
    @Transactional(readOnly = true)
    public TicketStatisticsDto getEventStatistics(Long eventId, UserContext user) {

        log.info("Récupération des statistiques pour l'événement {}", eventId);

        EventDetailsDto eventDetails = eventServiceClient.getEventById(eventId);
        requireEventManagement(eventDetails, user);

        List<Ticket> allTickets = ticketRepository.findByEventIdAndIsArchivedFalse(eventId);

        long totalTickets = allTickets.size();
        long paidTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.PAID).count();
        long reservedTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.RESERVED).count();
        long cancelledTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.CANCELLED).count();
        long usedTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.USED).count();
        long expiredTickets = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.EXPIRED).count();

        BigDecimal totalRevenue = allTickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.PAID)
                .map(Ticket::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageTicketPrice = paidTickets > 0
                ? totalRevenue.divide(
                BigDecimal.valueOf(paidTickets),
                2,
                java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long checkedInCount = ticketRepository.countByEventIdAndCheckedInTrueAndIsArchivedFalse(eventId);
        double checkInRate = paidTickets > 0
                ? (checkedInCount * 100.0) / paidTickets
                : 0.0;

        LocalDateTime now = LocalDateTime.now();
        long ticketsLast24h = allTickets.stream()
                .filter(t -> t.getCreatedAt().isAfter(now.minusHours(24))).count();
        long ticketsLast7days = allTickets.stream()
                .filter(t -> t.getCreatedAt().isAfter(now.minusDays(7))).count();
        long ticketsLast30days = allTickets.stream()
                .filter(t -> t.getCreatedAt().isAfter(now.minusDays(30))).count();

        return TicketStatisticsDto.builder()
                .eventId(eventId)
                .eventName(eventDetails.getName())
                .totalTickets(totalTickets)
                .paidTickets(paidTickets)
                .reservedTickets(reservedTickets)
                .cancelledTickets(cancelledTickets)
                .usedTickets(usedTickets)
                .expiredTickets(expiredTickets)
                .totalRevenue(totalRevenue)
                .averageTicketPrice(averageTicketPrice)
                .checkedInCount(checkedInCount)
                .checkInRate(checkInRate)
                .ticketsLast24h(ticketsLast24h)
                .ticketsLast7days(ticketsLast7days)
                .ticketsLast30days(ticketsLast30days)
                .build();
    }

    // UTILITAIRES

    private Ticket findTicketByUuid(String uuid) {
        return ticketRepository.findByUuid(uuid)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket non trouvé avec l'UUID : " + uuid
                ));
    }

    // Le détail d'un billet contient son QR code : le lire, c'est pouvoir entrer à sa place
    private void checkCanView(Ticket ticket, String userId, String roles) {
        if (!isAdmin(roles) && (userId == null || !userId.equals(ticket.getUserId()))) {
            throw new TicketAccessDeniedException("Vous n'avez pas accès à ce ticket");
        }
    }

    private boolean isAdmin(String roles) {
        return UserContext.of(null, roles, null).isAdmin();
    }

    // Billets et statistiques d'un événement : admin Ticket241 ou admin de sa structure organisatrice
    private void requireEventManagement(EventDetailsDto event, UserContext user) {
        if (!user.canManageStructure(event.getStructureId())) {
            throw new TicketAccessDeniedException("Seuls les organisateurs de cet événement peuvent consulter ses billets");
        }
    }

    // Structure organisatrice du billet ; les billets créés avant l'ajout de la colonne sont complétés
    // une fois auprès d'event-service (la valeur est enregistrée avec la validation du billet)
    private Long resolveStructureId(Ticket ticket) {
        if (ticket.getEventStructureId() == null) {
            ticket.setEventStructureId(eventServiceClient.getEventById(ticket.getEventId()).getStructureId());
        }
        return ticket.getEventStructureId();
    }

    // Nouveau jeton + contenu + image du QR code (création du billet et transfert)
    private void assignNewQrCode(Ticket ticket) {
        String validationToken = qrCodeService.newValidationToken();
        String payload = qrCodeService.buildQrPayload(ticket.getUuid(), ticket.getTicketNumber(), validationToken);
        ticket.setValidationToken(validationToken);
        ticket.setQrCode(payload);
        ticket.setQrCodeImageUrl(qrCodeService.generateQRCodeImage(payload));
    }
}
