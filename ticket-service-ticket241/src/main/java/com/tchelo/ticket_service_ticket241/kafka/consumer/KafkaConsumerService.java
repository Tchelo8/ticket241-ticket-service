package com.tchelo.ticket_service_ticket241.kafka.consumer;

import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.kafka.event.PaymentCompletedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.PaymentFailedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.SeatsDecreasedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.SeatsDecreaseFailedEvent;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.kafka.producer.TicketEvents;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Côté ticket-service de la saga. Chaque traitement ne fait passer un billet que depuis le statut
// attendu (PENDING → RESERVED, RESERVED → PAID…) : un message rejoué ou arrivé en retard ne peut
// donc ni ressusciter un billet expiré ni compter deux fois une commande.
// Une exception remonte au conteneur, qui rejoue le message puis l'envoie dans <topic>.DLT.
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final TicketRepository ticketRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${ticket.reservation.hold-minutes:10}")
    private long holdMinutes;

    // ÉCOUTE : places réservées par event-service → billets PENDING → RESERVED
    @KafkaListener(topics = "seats-decreased", groupId = "ticket-service-group")
    @Transactional
    public void handleSeatsDecreased(SeatsDecreasedEvent event) {
        log.info("Événement reçu : seats-decreased pour la commande {}", event.getOrderId());

        if (Boolean.FALSE.equals(event.getSuccess())) {
            cancelPendingTickets(event.getOrderId(), event.getMessage());
            return;
        }

        List<Ticket> tickets = ticketRepository.findByOrderIdAndIsArchivedFalse(event.getOrderId());
        if (tickets.isEmpty()) {
            log.error("Places réservées pour la commande {} mais aucun billet en base", event.getOrderId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int confirmed = 0;
        for (Ticket ticket : tickets) {
            // Un billet déjà expiré (resté PENDING trop longtemps) n'est pas ressuscité : son
            // message reservation-expired, publié après ticket-reserved, libérera la place
            if (ticket.getStatus() != TicketStatus.PENDING) {
                log.info("Billet {} ignoré (statut {})", ticket.getTicketNumber(), ticket.getStatus());
                continue;
            }
            ticket.changeStatus(TicketStatus.RESERVED, "system", "Places confirmées par event-service");
            ticket.setReservationDate(now);
            ticket.setExpirationDate(now.plusMinutes(holdMinutes));
            ticket.setKafkaEventId(event.getKafkaEventId());
            ticket.setLastKafkaEvent("SEATS_DECREASED");
            confirmed++;
        }

        log.info("{} billet(s) de la commande {} réservé(s) jusqu'à {}",
                confirmed, event.getOrderId(), now.plusMinutes(holdMinutes));
    }

    // ÉCOUTE : réservation refusée par event-service → billets PENDING annulés (aucune place à libérer)
    @KafkaListener(topics = "seats-decrease-failed", groupId = "ticket-service-group")
    @Transactional
    public void handleSeatsDecreaseFailed(SeatsDecreaseFailedEvent event) {
        log.info("Événement reçu : seats-decrease-failed pour la commande {} : {}",
                event.getOrderId(), event.getFailureReason());
        cancelPendingTickets(event.getOrderId(), event.getFailureReason());
    }

    // ÉCOUTE : paiement confirmé (futur payment-service) → billets RESERVED → PAID
    @KafkaListener(topics = "payment-completed", groupId = "ticket-service-group")
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Événement reçu : payment-completed pour le paiement {}", event.getPaymentId());

        List<Ticket> tickets = ticketRepository.findByPaymentIdAndIsArchivedFalse(event.getPaymentId());
        if (tickets.isEmpty()) {
            log.error("Paiement {} confirmé mais aucun billet associé", event.getPaymentId());
            return;
        }

        List<Ticket> paidNow = new ArrayList<>();
        for (Ticket ticket : tickets) {
            switch (ticket.getStatus()) {
                case RESERVED -> {
                    ticket.confirmPurchase(event.getPaymentId(), event.getPaymentMethod(), event.getPaymentProvider());
                    ticket.setPaymentTransactionId(event.getTransactionId());
                    if (event.getCompletedAt() != null) {
                        ticket.setPaymentCompletedAt(event.getCompletedAt());
                    }
                    ticket.setKafkaEventId(event.getKafkaEventId());
                    ticket.setLastKafkaEvent("PAYMENT_COMPLETED");
                    paidNow.add(ticket);
                }
                case PAID, USED -> log.info("Billet {} déjà payé, message ignoré", ticket.getTicketNumber());
                default -> {
                    // Le paiement est arrivé après l'expiration / l'annulation : la place a déjà été
                    // rendue, on ne valide pas le billet mais on garde la trace pour rembourser
                    ticket.setPaymentStatus(PaymentStatus.COMPLETED);
                    ticket.setPaymentTransactionId(event.getTransactionId());
                    ticket.setInternalNotes("REMBOURSEMENT À EFFECTUER : paiement " + event.getPaymentId()
                            + " reçu alors que le billet était " + ticket.getStatus());
                    log.error("Paiement {} reçu pour le billet {} en statut {} : remboursement à effectuer",
                            event.getPaymentId(), ticket.getTicketNumber(), ticket.getStatus());
                }
            }
        }

        if (!paidNow.isEmpty()) {
            kafkaProducerService.publishTicketPaidEvent(TicketEvents.ticketsPaid(paidNow, event.getPaymentId()));
            kafkaProducerService.publishTicketConfirmedEvent(TicketEvents.ticketsConfirmed(paidNow, event.getPaymentId()));
            log.info("{} billet(s) payé(s) pour la commande {}", paidNow.size(), paidNow.get(0).getOrderId());
        }
    }

    // ÉCOUTE : paiement échoué (futur payment-service) → billets RESERVED annulés et places libérées
    @KafkaListener(topics = "payment-failed", groupId = "ticket-service-group")
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Événement reçu : payment-failed pour le paiement {}", event.getPaymentId());

        List<Ticket> tickets = ticketRepository.findByPaymentIdAndIsArchivedFalse(event.getPaymentId());
        String reason = "Paiement échoué : " + event.getFailureReason();

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() != TicketStatus.RESERVED) {
                log.info("Billet {} ignoré (statut {})", ticket.getTicketNumber(), ticket.getStatus());
                continue;
            }
            ticket.changeStatus(TicketStatus.CANCELLED, "system", reason);
            ticket.setCancelledAt(event.getFailedAt() != null ? event.getFailedAt() : LocalDateTime.now());
            ticket.setCancellationReason(reason);
            ticket.setPaymentStatus(PaymentStatus.FAILED);
            ticket.setKafkaEventId(event.getKafkaEventId());
            ticket.setLastKafkaEvent("PAYMENT_FAILED");

            kafkaProducerService.publishTicketCancelledEvent(TicketEvents.ticketCancelled(ticket, TicketStatus.RESERVED));
        }
    }

    private void cancelPendingTickets(String orderId, String reason) {
        List<Ticket> tickets = ticketRepository.findByOrderIdAndIsArchivedFalse(orderId);
        String cancellationReason = "Réservation refusée : " + reason;

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() != TicketStatus.PENDING) {
                continue;
            }
            ticket.changeStatus(TicketStatus.CANCELLED, "system", cancellationReason);
            ticket.setCancelledAt(LocalDateTime.now());
            ticket.setCancellationReason(cancellationReason);
            ticket.setLastKafkaEvent("SEATS_DECREASE_FAILED");
        }
        log.info("Commande {} refusée par event-service : {}", orderId, reason);
    }
}
