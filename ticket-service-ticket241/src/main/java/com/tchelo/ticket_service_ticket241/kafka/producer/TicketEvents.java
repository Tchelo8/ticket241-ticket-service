package com.tchelo.ticket_service_ticket241.kafka.producer;

import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.kafka.event.CategoryQuantity;
import com.tchelo.ticket_service_ticket241.kafka.event.ReservationExpiredEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketCancelledEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketConfirmedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketPaidEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Construction des messages Kafka à partir des billets, partagée par le service, le consommateur
// et le job planifié pour que les champs attendus par event-service soient toujours remplis pareil
public final class TicketEvents {

    private TicketEvents() {
    }

    public static ReservationExpiredEvent reservationExpired(Ticket ticket) {
        return ReservationExpiredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .eventIdLong(ticket.getEventId())
                .categoryId(ticket.getTicketCategoryId())
                .quantity(1)
                .orderId(ticket.getOrderId())
                .expiredAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // previousStatus = statut du billet juste avant l'annulation (RESERVED ou PAID)
    public static TicketCancelledEvent ticketCancelled(Ticket ticket, TicketStatus previousStatus) {
        return TicketCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .orderId(ticket.getOrderId())
                .previousStatus(previousStatus != null ? previousStatus.name() : null)
                .eventIdLong(ticket.getEventId())
                .categoryId(ticket.getTicketCategoryId())
                .quantity(1)
                .userId(ticket.getUserId())
                .reason(ticket.getCancellationReason())
                .cancelledAt(ticket.getCancelledAt())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Billets payés d'une même commande → contenu de l'email de confirmation
    public static TicketConfirmedEvent ticketsConfirmed(List<Ticket> paidTickets, String paymentId) {
        Ticket first = paidTickets.get(0);
        return TicketConfirmedEvent.builder()
                .orderId(first.getOrderId())
                .paymentId(paymentId)
                .userId(first.getUserId())
                .recipientEmail(first.getUserEmail())
                .recipientFirstName(first.getUserFirstName())
                .recipientLastName(first.getUserLastName())
                .eventName(first.getEventName())
                .eventStartDate(first.getEventStartDate())
                .eventVenueName(first.getEventVenueName())
                .eventVenueCityName(first.getEventVenueCityName())
                .ticketCount(paidTickets.size())
                .totalAmount(paidTickets.stream()
                        .map(Ticket::getFinalPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .currency(first.getCurrency())
                .tickets(paidTickets.stream()
                        .map(ticket -> TicketConfirmedEvent.TicketLine.builder()
                                .ticketNumber(ticket.getTicketNumber())
                                .categoryName(ticket.getTicketCategoryName())
                                .holderName(holderName(ticket))
                                .finalPrice(ticket.getFinalPrice())
                                .build())
                        .toList())
                .confirmedAt(LocalDateTime.now())
                .build();
    }

    // Bénéficiaire (cadeau) en priorité, sinon l'acheteur ; null si aucun nom n'a été saisi
    private static String holderName(Ticket ticket) {
        String beneficiary = ((ticket.getBeneficiaryFirstName() != null ? ticket.getBeneficiaryFirstName() : "")
                + " " + (ticket.getBeneficiaryLastName() != null ? ticket.getBeneficiaryLastName() : "")).trim();
        if (!beneficiary.isEmpty()) {
            return beneficiary;
        }
        String owner = ((ticket.getUserFirstName() != null ? ticket.getUserFirstName() : "")
                + " " + (ticket.getUserLastName() != null ? ticket.getUserLastName() : "")).trim();
        return owner.isEmpty() ? null : owner;
    }

    // Une commande peut mélanger plusieurs catégories : event-service vend les places ligne par ligne.
    // categoryId n'est renseigné que pour une commande d'une seule catégorie (ancien format).
    public static TicketPaidEvent ticketsPaid(List<Ticket> paidTickets, String paymentId) {
        Ticket first = paidTickets.get(0);
        Map<Long, Long> perCategory = paidTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getTicketCategoryId, LinkedHashMap::new, Collectors.counting()));
        return TicketPaidEvent.builder()
                .orderId(first.getOrderId())
                .paymentId(paymentId)
                .eventId(first.getEventId())
                .categoryId(perCategory.size() == 1 ? first.getTicketCategoryId() : null)
                .quantity(paidTickets.size())
                .lines(perCategory.entrySet().stream()
                        .map(entry -> CategoryQuantity.builder()
                                .ticketCategoryId(entry.getKey())
                                .quantity(entry.getValue().intValue())
                                .build())
                        .toList())
                .ticketIds(paidTickets.stream().map(Ticket::getId).toList())
                .paidAt(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
