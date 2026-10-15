package com.tchelo.ticket_service_ticket241.scheduler;

import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.kafka.producer.TicketEvents;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketScheduledTasks {

    private final TicketRepository ticketRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${ticket.reservation.pending-timeout-minutes:5}")
    private long pendingTimeoutMinutes;

    // Billets RESERVED dont le délai est dépassé (pas de paiement lancé à temps, ou fenêtre de
    // paiement écoulée) → EXPIRED, et event-service libère leurs places.
    // Les messages partent après le commit (KafkaProducerService).
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanExpiredReservations() {
        List<Ticket> expiredTickets = ticketRepository.findExpiredReservations(LocalDateTime.now());
        if (expiredTickets.isEmpty()) {
            log.debug("Aucune réservation expirée trouvée");
            return;
        }

        for (Ticket ticket : expiredTickets) {
            ticket.expire();
            kafkaProducerService.publishReservationExpiredEvent(TicketEvents.reservationExpired(ticket));
            log.info("Billet {} expiré (réservation non payée)", ticket.getTicketNumber());
        }
        log.info("{} réservation(s) expirée(s)", expiredTickets.size());
    }

    // Billets restés PENDING : event-service n'a jamais répondu (message perdu, service arrêté…).
    // On les expire aussi, et on prévient event-service : s'il avait réservé les places il les
    // libère, sinon il refusera la réservation si elle arrive plus tard.
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanStalePendingTickets() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Ticket> staleTickets = ticketRepository.findStalePendingTickets(threshold);
        if (staleTickets.isEmpty()) {
            return;
        }

        for (Ticket ticket : staleTickets) {
            ticket.changeStatus(TicketStatus.EXPIRED, "system",
                    "Places jamais confirmées par event-service après " + pendingTimeoutMinutes + " min");
            kafkaProducerService.publishReservationExpiredEvent(TicketEvents.reservationExpired(ticket));
        }
        log.warn("{} billet(s) resté(s) PENDING plus de {} min expiré(s) : vérifier event-service et Kafka",
                staleTickets.size(), pendingTimeoutMinutes);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanOldData() {
        log.debug("Nettoyage des anciennes données...");

        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        List<Ticket> oldCancelledTickets = ticketRepository.findByStatusInAndIsArchivedFalse(
                        List.of(TicketStatus.CANCELLED, TicketStatus.EXPIRED)
                ).stream()
                .filter(t -> t.getCreatedAt().isBefore(sixMonthsAgo))
                .toList();

        if (!oldCancelledTickets.isEmpty()) {
            for (Ticket ticket : oldCancelledTickets) {
                ticket.setIsArchived(true);
                ticket.setArchivedAt(LocalDateTime.now());
                ticket.setArchivedBy("system");
            }
            log.info("{} tickets archivés", oldCancelledTickets.size());
        }
    }
}
