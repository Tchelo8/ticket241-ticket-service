package com.tchelo.ticket_service_ticket241.kafka.producer;

import com.tchelo.ticket_service_ticket241.kafka.event.ReservationExpiredEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketCancelledEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketConfirmedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketPaidEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketReservedEvent;

import java.util.Map;

// Tous les messages d'une commande ont l'orderId pour clé (même partition → lus dans l'ordre par
// event-service) et ne partent qu'après le commit de la transaction en cours : un consommateur ne
// peut jamais recevoir un message sur des billets qui n'existent pas (encore) en base.
public interface KafkaProducerService {

    // Billets créés : event-service doit réserver les places
    void publishTicketReservedEvent(TicketReservedEvent event);

    // Demande de paiement (consommée par le futur payment-service)
    void publishTicketPurchaseRequestedEvent(String orderId, Map<String, Object> event);

    // Paiement confirmé : event-service passe les places en "vendues"
    void publishTicketPaidEvent(TicketPaidEvent event);

    // Commande payée : notification-service envoie l'email de confirmation avec le PDF des billets
    void publishTicketConfirmedEvent(TicketConfirmedEvent event);

    // Billet annulé : event-service libère une place réservée ou vendue
    void publishTicketCancelledEvent(TicketCancelledEvent event);

    // Billet validé à l'entrée
    void publishTicketValidatedEvent(String orderId, Map<String, Object> event);

    // Réservation expirée : event-service libère la place réservée
    void publishReservationExpiredEvent(ReservationExpiredEvent event);
}
