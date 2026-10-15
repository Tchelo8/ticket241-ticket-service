package com.tchelo.ticket_service_ticket241.kafka.producer;

import com.tchelo.ticket_service_ticket241.kafka.event.ReservationExpiredEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketCancelledEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketConfirmedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketPaidEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishTicketReservedEvent(TicketReservedEvent event) {
        publishAfterCommit("ticket-reserved", event.getOrderId(), event);
    }

    @Override
    public void publishTicketPurchaseRequestedEvent(String orderId, Map<String, Object> event) {
        publishAfterCommit("ticket-purchase-requested", orderId, event);
    }

    @Override
    public void publishTicketPaidEvent(TicketPaidEvent event) {
        publishAfterCommit("ticket-paid", event.getOrderId(), event);
    }

    @Override
    public void publishTicketConfirmedEvent(TicketConfirmedEvent event) {
        publishAfterCommit("ticket-confirmed", event.getOrderId(), event);
    }

    @Override
    public void publishTicketCancelledEvent(TicketCancelledEvent event) {
        publishAfterCommit("ticket-cancelled", event.getOrderId(), event);
    }

    @Override
    public void publishTicketValidatedEvent(String orderId, Map<String, Object> event) {
        publishAfterCommit("ticket-validated", orderId, event);
    }

    @Override
    public void publishReservationExpiredEvent(ReservationExpiredEvent event) {
        publishAfterCommit("reservation-expired", event.getOrderId(), event);
    }

    // Dans une transaction : envoi différé après le commit (rien ne part si elle est annulée).
    // Hors transaction : envoi immédiat.
    private void publishAfterCommit(String topic, String key, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, payload);
                }
            });
        } else {
            send(topic, key, payload);
        }
    }

    private void send(String topic, String key, Object payload) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Événement publié → topic {}, commande {}, partition {}, offset {}",
                    topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Publication interrompue → topic {}, commande {}", topic, key);
        } catch (Exception e) {
            // La base est déjà commitée : on ne peut plus annuler. Les billets PENDING / RESERVED
            // concernés seront rattrapés par les délais d'expiration de TicketScheduledTasks.
            log.error("ÉCHEC de publication → topic {}, commande {}, message perdu : {}", topic, key, payload, e);
        }
    }
}
