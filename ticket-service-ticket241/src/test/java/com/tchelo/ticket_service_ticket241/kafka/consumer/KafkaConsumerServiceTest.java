package com.tchelo.ticket_service_ticket241.kafka.consumer;

import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.kafka.event.PaymentCompletedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.PaymentFailedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.SeatsDecreaseFailedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.SeatsDecreasedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketCancelledEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketConfirmedEvent;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketPaidEvent;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private KafkaProducerService kafkaProducerService;

    private KafkaConsumerService consumer;

    @BeforeEach
    void setUp() {
        consumer = new KafkaConsumerService(ticketRepository, kafkaProducerService);
        ReflectionTestUtils.setField(consumer, "holdMinutes", 10L);
    }

    private Ticket ticket(long id, TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-2026-" + id)
                .userId("+24100000001")
                .userEmail("awa@test.com")
                .orderId("order-1")
                .eventId(1L)
                .ticketCategoryId(10L)
                .paymentId("PAY-1")
                .finalPrice(new BigDecimal("12500"))
                .status(status)
                .build();
        ticket.setId(id);
        return ticket;
    }

    @Test
    void confirmedSeatsMovePendingTicketsToReserved() {
        Ticket pending = ticket(1, TicketStatus.PENDING);
        Ticket expired = ticket(2, TicketStatus.EXPIRED);
        when(ticketRepository.findByOrderIdAndIsArchivedFalse("order-1")).thenReturn(List.of(pending, expired));

        consumer.handleSeatsDecreased(SeatsDecreasedEvent.builder().orderId("order-1").success(true).build());

        assertThat(pending.getStatus()).isEqualTo(TicketStatus.RESERVED);
        assertThat(pending.getExpirationDate()).isAfter(LocalDateTime.now().plusMinutes(9));
        // Un billet expiré entre-temps n'est pas ressuscité
        assertThat(expired.getStatus()).isEqualTo(TicketStatus.EXPIRED);
    }

    @Test
    void refusedReservationCancelsPendingTickets() {
        Ticket pending = ticket(1, TicketStatus.PENDING);
        when(ticketRepository.findByOrderIdAndIsArchivedFalse("order-1")).thenReturn(List.of(pending));

        consumer.handleSeatsDecreaseFailed(SeatsDecreaseFailedEvent.builder()
                .orderId("order-1").failureReason("Plus assez de places").build());

        assertThat(pending.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(pending.getCancellationReason()).contains("Plus assez de places");
    }

    @Test
    void completedPaymentMarksTicketsPaidAndTellsEventService() {
        Ticket first = ticket(1, TicketStatus.RESERVED);
        Ticket second = ticket(2, TicketStatus.RESERVED);
        when(ticketRepository.findByPaymentIdAndIsArchivedFalse("PAY-1")).thenReturn(List.of(first, second));

        consumer.handlePaymentCompleted(PaymentCompletedEvent.builder().paymentId("PAY-1").build());

        assertThat(first.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(second.getStatus()).isEqualTo(TicketStatus.PAID);
        ArgumentCaptor<TicketPaidEvent> sent = ArgumentCaptor.forClass(TicketPaidEvent.class);
        verify(kafkaProducerService).publishTicketPaidEvent(sent.capture());
        assertThat(sent.getValue().getQuantity()).isEqualTo(2);
        assertThat(sent.getValue().getOrderId()).isEqualTo("order-1");

        // Et l'email de confirmation est demandé à notification-service
        ArgumentCaptor<TicketConfirmedEvent> confirmed = ArgumentCaptor.forClass(TicketConfirmedEvent.class);
        verify(kafkaProducerService).publishTicketConfirmedEvent(confirmed.capture());
        assertThat(confirmed.getValue().getRecipientEmail()).isEqualTo("awa@test.com");
        assertThat(confirmed.getValue().getTicketCount()).isEqualTo(2);
        assertThat(confirmed.getValue().getTotalAmount()).isEqualByComparingTo("25000");
        assertThat(confirmed.getValue().getTickets()).extracting(TicketConfirmedEvent.TicketLine::getTicketNumber)
                .containsExactly("TKT-2026-1", "TKT-2026-2");
    }

    @Test
    void paymentArrivingAfterExpiryIsFlaggedForRefundNotValidated() {
        Ticket expired = ticket(1, TicketStatus.EXPIRED);
        when(ticketRepository.findByPaymentIdAndIsArchivedFalse("PAY-1")).thenReturn(List.of(expired));

        consumer.handlePaymentCompleted(PaymentCompletedEvent.builder().paymentId("PAY-1").build());

        assertThat(expired.getStatus()).isEqualTo(TicketStatus.EXPIRED);
        assertThat(expired.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(expired.getInternalNotes()).contains("REMBOURSEMENT");
        verify(kafkaProducerService, never()).publishTicketPaidEvent(any());
    }

    @Test
    void replayedPaymentDoesNotSellTwice() {
        Ticket paid = ticket(1, TicketStatus.PAID);
        when(ticketRepository.findByPaymentIdAndIsArchivedFalse("PAY-1")).thenReturn(List.of(paid));

        consumer.handlePaymentCompleted(PaymentCompletedEvent.builder().paymentId("PAY-1").build());

        verify(kafkaProducerService, never()).publishTicketPaidEvent(any());
    }

    @Test
    void failedPaymentCancelsAndReleasesReservedSeats() {
        Ticket reserved = ticket(1, TicketStatus.RESERVED);
        when(ticketRepository.findByPaymentIdAndIsArchivedFalse("PAY-1")).thenReturn(List.of(reserved));

        consumer.handlePaymentFailed(PaymentFailedEvent.builder().paymentId("PAY-1").failureReason("Solde insuffisant").build());

        assertThat(reserved.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        ArgumentCaptor<TicketCancelledEvent> sent = ArgumentCaptor.forClass(TicketCancelledEvent.class);
        verify(kafkaProducerService).publishTicketCancelledEvent(sent.capture());
        assertThat(sent.getValue().getPreviousStatus()).isEqualTo("RESERVED");
    }
}
