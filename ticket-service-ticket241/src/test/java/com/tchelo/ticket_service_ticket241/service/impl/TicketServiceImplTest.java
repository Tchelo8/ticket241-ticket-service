package com.tchelo.ticket_service_ticket241.service.impl;

import com.tchelo.ticket_service_ticket241.client.EventServiceClient;
import com.tchelo.ticket_service_ticket241.client.dto.EventDetailsDto;
import com.tchelo.ticket_service_ticket241.client.dto.TicketCategoryDetailsDto;
import com.tchelo.ticket_service_ticket241.dto.request.CancelTicketDto;
import com.tchelo.ticket_service_ticket241.dto.request.PurchaseTicketsDto;
import com.tchelo.ticket_service_ticket241.dto.request.ReserveTicketsDto;
import com.tchelo.ticket_service_ticket241.dto.request.TransferTicketDto;
import com.tchelo.ticket_service_ticket241.dto.response.ReservationResponseDto;
import com.tchelo.ticket_service_ticket241.dto.response.TicketPdfDto;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.exception.InvalidTicketOperationException;
import com.tchelo.ticket_service_ticket241.exception.TicketAccessDeniedException;
import com.tchelo.ticket_service_ticket241.kafka.event.TicketCancelledEvent;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.mapper.TicketMapper;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceImplTest {

    private static final String USER = "+24100000001";
    private static final Long EVENT_ID = 1L;
    private static final Long CATEGORY_ID = 10L;

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventServiceClient eventServiceClient;
    @Mock
    private KafkaProducerService kafkaProducerService;

    private TicketServiceImpl service;
    private EventDetailsDto event;
    private TicketCategoryDetailsDto category;

    @BeforeEach
    void setUp() {
        QRCodeServiceImpl qrCodeService = new QRCodeServiceImpl();
        service = new TicketServiceImpl(ticketRepository, eventServiceClient, new TicketMapper(),
                qrCodeService, kafkaProducerService, new TicketPdfServiceImpl(qrCodeService));
        ReflectionTestUtils.setField(service, "paymentWindowMinutes", 15L);

        event = EventDetailsDto.builder()
                .id(EVENT_ID).uuid("event-uuid").name("Concert").slug("concert")
                .startDate(LocalDateTime.now().plusDays(10))
                .maxTicketsPerUser(4)
                .build();
        category = TicketCategoryDetailsDto.builder()
                .id(CATEGORY_ID).name("VIP").price(new BigDecimal("10000"))
                .availableSeats(50).isActive(true).maxTicketsPerOrder(10)
                .build();

        when(eventServiceClient.getEventById(EVENT_ID)).thenReturn(event);
        when(eventServiceClient.getTicketCategory(EVENT_ID, CATEGORY_ID)).thenReturn(category);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReserveTicketsDto reserve(int quantity) {
        return ReserveTicketsDto.builder().eventId(EVENT_ID).ticketCategoryId(CATEGORY_ID).quantity(quantity).build();
    }

    private Ticket ticket(TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-2026-ABCDEFGHJK")
                .userId(USER)
                .orderId("order-1")
                .eventId(EVENT_ID)
                .ticketCategoryId(CATEGORY_ID)
                .eventStartDate(LocalDateTime.now().plusDays(10))
                .basePrice(new BigDecimal("10000"))
                .finalPrice(new BigDecimal("12500"))
                .status(status)
                .build();
        ticket.setId(100L);
        ticket.setUuid("3f2b1c4d-1111-2222-3333-444455556666");
        ticket.setIsArchived(false);
        return ticket;
    }

    @Test
    void reservationCreatesPendingTicketsWithUniqueQrCodes() {
        ReservationResponseDto response = service.reserveTickets(reserve(2), USER);

        assertThat(response.getTickets()).hasSize(2);
        assertThat(response.getTickets()).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(TicketStatus.PENDING);
            assertThat(t.getTicketNumber()).matches("TKT-\\d{4}-[A-Z2-9]{10}");
            assertThat(t.getQrCode()).startsWith(t.getUuid() + "|" + t.getTicketNumber() + "|");
        });
        assertThat(response.getTickets().get(0).getQrCode()).isNotEqualTo(response.getTickets().get(1).getQrCode());
        verify(kafkaProducerService).publishTicketReservedEvent(any());
    }

    @Test
    void reservationIsRefusedWhenSaleIsClosed() {
        event.setSaleClosedReason("L'événement n'est pas ouvert à la vente (statut : DRAFT)");

        assertThatThrownBy(() -> service.reserveTickets(reserve(1), USER))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("DRAFT");
        verify(kafkaProducerService, never()).publishTicketReservedEvent(any());
    }

    @Test
    void reservationRespectsMaxTicketsPerUser() {
        when(ticketRepository.countByUserIdAndEventIdAndStatusInAndIsArchivedFalse(anyString(), anyLong(), anyList()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.reserveTickets(reserve(2), USER))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("Limite de 4 billet(s)");
    }

    @Test
    void reservationRespectsMaxTicketsPerOrder() {
        category.setMaxTicketsPerOrder(2);

        assertThatThrownBy(() -> service.reserveTickets(reserve(3), USER))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("Maximum 2 billet(s) par commande");
    }

    @Test
    void purchaseOfPendingTicketIsRefused() {
        when(ticketRepository.findAllById(List.of(100L))).thenReturn(List.of(ticket(TicketStatus.PENDING)));
        PurchaseTicketsDto dto = PurchaseTicketsDto.builder()
                .ticketIds(List.of(100L)).paymentMethod(PaymentMethod.AIRTEL_MONEY).build();

        assertThatThrownBy(() -> service.purchaseTickets(dto, USER))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("en cours de confirmation");
    }

    @Test
    void purchaseExtendsReservationForThePaymentWindow() {
        Ticket reserved = ticket(TicketStatus.RESERVED);
        reserved.setExpirationDate(LocalDateTime.now().plusMinutes(2));
        when(ticketRepository.findAllById(List.of(100L))).thenReturn(List.of(reserved));
        PurchaseTicketsDto dto = PurchaseTicketsDto.builder()
                .ticketIds(List.of(100L)).paymentMethod(PaymentMethod.AIRTEL_MONEY).build();

        service.purchaseTickets(dto, USER);

        assertThat(reserved.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(reserved.getExpirationDate()).isAfter(LocalDateTime.now().plusMinutes(14));

        // Un second paiement sur les mêmes billets est refusé
        assertThatThrownBy(() -> service.purchaseTickets(dto, USER))
                .hasMessageContaining("paiement est déjà en cours");
    }

    @Test
    void cancellingAReservedTicketTellsEventServiceToReleaseAReservedSeat() {
        Ticket reserved = ticket(TicketStatus.RESERVED);
        when(ticketRepository.findByUuid(reserved.getUuid())).thenReturn(Optional.of(reserved));

        service.cancelTicket(reserved.getUuid(), CancelTicketDto.builder().reason("Je ne peux plus venir").build(), USER);

        ArgumentCaptor<TicketCancelledEvent> sent = ArgumentCaptor.forClass(TicketCancelledEvent.class);
        verify(kafkaProducerService).publishTicketCancelledEvent(sent.capture());
        assertThat(sent.getValue().getPreviousStatus()).isEqualTo("RESERVED");
        assertThat(sent.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(reserved.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void cancellingDuringPaymentIsRefused() {
        Ticket reserved = ticket(TicketStatus.RESERVED);
        reserved.setPaymentStatus(PaymentStatus.PROCESSING);
        when(ticketRepository.findByUuid(reserved.getUuid())).thenReturn(Optional.of(reserved));

        assertThatThrownBy(() -> service.cancelTicket(reserved.getUuid(),
                CancelTicketDto.builder().reason("Je ne peux plus venir").build(), USER))
                .hasMessageContaining("paiement est en cours");
    }

    @Test
    void transferKeepsTicketValidAndReplacesItsQrCode() {
        Ticket paid = ticket(TicketStatus.PAID);
        paid.setQrCode("old-qr");
        paid.setUserEmail("old@test.com");
        when(ticketRepository.findByUuid(paid.getUuid())).thenReturn(Optional.of(paid));

        service.transferTicket(paid.getUuid(), TransferTicketDto.builder()
                .newUserId("+24100000002").newUserEmail("new@test.com").build(), USER);

        assertThat(paid.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(paid.getUserId()).isEqualTo("+24100000002");
        assertThat(paid.getUserEmail()).isEqualTo("new@test.com");
        assertThat(paid.getQrCode()).isNotEqualTo("old-qr").startsWith(paid.getUuid() + "|");
        assertThat(paid.getTransferredFromUserId()).isEqualTo(USER);
    }

    @Test
    void paidTicketCanBeDownloadedByItsOwnerOnly() {
        Ticket paid = ticket(TicketStatus.PAID);
        paid.setQrCode(paid.getUuid() + "|" + paid.getTicketNumber() + "|jeton");
        when(ticketRepository.findByUuid(paid.getUuid())).thenReturn(Optional.of(paid));

        TicketPdfDto pdf = service.downloadTicket(paid.getUuid(), USER, "ROLE_USER");

        assertThat(pdf.getFileName()).isEqualTo("billet-TKT-2026-ABCDEFGHJK.pdf");
        assertThat(new String(pdf.getContent(), 0, 5)).isEqualTo("%PDF-");
        assertThatThrownBy(() -> service.downloadTicket(paid.getUuid(), "+24199999999", "ROLE_USER"))
                .isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    void unpaidTicketCannotBeDownloaded() {
        Ticket reserved = ticket(TicketStatus.RESERVED);
        when(ticketRepository.findByUuid(reserved.getUuid())).thenReturn(Optional.of(reserved));

        assertThatThrownBy(() -> service.downloadTicket(reserved.getUuid(), USER, "ROLE_USER"))
                .isInstanceOf(InvalidTicketOperationException.class)
                .hasMessageContaining("payés");
    }

    @Test
    void orderDownloadOnlyContainsPaidTickets() {
        Ticket paid = ticket(TicketStatus.PAID);
        paid.setQrCode(paid.getUuid() + "|" + paid.getTicketNumber() + "|jeton");
        Ticket expired = ticket(TicketStatus.EXPIRED);
        when(ticketRepository.findByOrderIdAndIsArchivedFalse("order-1")).thenReturn(List.of(paid, expired));

        TicketPdfDto pdf = service.getOrderTicketsPdfForConfirmation("order-1");

        assertThat(pdf.getFileName()).isEqualTo("billets-ticket241-order-1.pdf");
        assertThat(pdf.getContent()).isNotEmpty();
    }

    @Test
    void anotherUserCannotReadATicket() {
        Ticket paid = ticket(TicketStatus.PAID);
        when(ticketRepository.findByUuid(paid.getUuid())).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.getTicketByUuid(paid.getUuid(), "+24199999999", "ROLE_USER"))
                .isInstanceOf(TicketAccessDeniedException.class);
        assertThat(service.getTicketByUuid(paid.getUuid(), "+24199999999", "ROLE_ADMIN")).isNotNull();
        assertThat(service.getTicketByUuid(paid.getUuid(), USER, "ROLE_USER")).isNotNull();
    }
}
