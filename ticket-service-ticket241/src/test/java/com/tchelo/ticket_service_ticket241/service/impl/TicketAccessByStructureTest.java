package com.tchelo.ticket_service_ticket241.service.impl;

import com.tchelo.ticket_service_ticket241.client.EventServiceClient;
import com.tchelo.ticket_service_ticket241.client.dto.EventDetailsDto;
import com.tchelo.ticket_service_ticket241.dto.request.ValidateTicketDto;
import com.tchelo.ticket_service_ticket241.dto.response.ValidationResponseDto;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.exception.TicketAccessDeniedException;
import com.tchelo.ticket_service_ticket241.kafka.producer.KafkaProducerService;
import com.tchelo.ticket_service_ticket241.mapper.TicketMapper;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import com.tchelo.ticket_service_ticket241.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Validation à l'entrée, billets et statistiques d'un événement : qui a le droit de faire quoi
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketAccessByStructureTest {

    private static final String QR = "3f2b1c4d-1111-2222-3333-444455556666|TKT-2026-ABCDEFGHJK|jeton";

    private static final UserContext ADMIN = UserContext.of("+24100000000", "ROLE_ADMIN", null);
    private static final UserContext USER = UserContext.of("+24100000001", "ROLE_USER", null);
    private static final UserContext AGENT_OF_1 = UserContext.of("+24100000002", "ROLE_AGENT", "1");
    private static final UserContext AGENT_OF_2 = UserContext.of("+24100000003", "ROLE_AGENT", "2");
    private static final UserContext STRUCTURE_ADMIN_OF_1 = UserContext.of("+24100000004", "ROLE_STRUCTURE_ADMIN", "1");
    private static final UserContext STRUCTURE_ADMIN_OF_2 = UserContext.of("+24100000005", "ROLE_STRUCTURE_ADMIN", "2");

    @Mock private TicketRepository ticketRepository;
    @Mock private EventServiceClient eventServiceClient;
    @Mock private KafkaProducerService kafkaProducerService;

    private TicketServiceImpl service;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        QRCodeServiceImpl qrCodeService = new QRCodeServiceImpl();
        service = new TicketServiceImpl(ticketRepository, eventServiceClient, new TicketMapper(),
                qrCodeService, kafkaProducerService, new TicketPdfServiceImpl(qrCodeService));

        ticket = Ticket.builder()
                .ticketNumber("TKT-2026-ABCDEFGHJK")
                .qrCode(QR)
                .userId("+24100000009")
                .orderId("order-1")
                .eventId(1L)
                .eventStructureId(1L)
                .eventName("Concert")
                // validable aujourd'hui : l'événement commence dans une minute
                .eventStartDate(LocalDateTime.now().plusMinutes(1))
                .finalPrice(new BigDecimal("12500"))
                .status(TicketStatus.PAID)
                .build();
        ticket.setId(100L);
        ticket.setIsArchived(false);

        when(ticketRepository.findByQrCode(QR)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventServiceClient.getEventById(1L)).thenReturn(EventDetailsDto.builder().id(1L).name("Concert").structureId(1L).build());
        when(ticketRepository.findByEventIdAndIsArchivedFalseOrderByCreatedAtDesc(any(), any())).thenReturn(Page.empty());
        when(ticketRepository.findByEventIdAndIsArchivedFalse(1L)).thenReturn(List.of());
    }

    private ValidationResponseDto scan(UserContext user) {
        return service.validateTicket(ValidateTicketDto.builder().qrCode(QR).validatedBy(user.userId()).build(), user);
    }

    @Test
    void anAgentScansTicketsOfItsOwnStructure() {
        ValidationResponseDto result = scan(AGENT_OF_1);

        assertThat(result.getIsValid()).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
    }

    @Test
    void anAgentCannotLetInTicketsOfAnotherOrganizer() {
        ValidationResponseDto result = scan(AGENT_OF_2);

        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getValidationResult()).isEqualTo("WRONG_ORGANIZER");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
    }

    @Test
    void structureAdminsAndPlatformAdminsCanScan() {
        assertThat(scan(STRUCTURE_ADMIN_OF_1).getIsValid()).isTrue();

        ticket.changeStatus(TicketStatus.PAID, "test", "remise à zéro");
        assertThat(scan(ADMIN).getIsValid()).isTrue();
    }

    @Test
    void aSimpleUserCannotUseTheScannerAtAll() {
        assertThatThrownBy(() -> scan(USER)).isInstanceOf(TicketAccessDeniedException.class);
        verify(ticketRepository, never()).findByQrCode(any());
    }

    @Test
    void oldTicketsWithoutStructureAreCompletedFromEventService() {
        ticket.setEventStructureId(null);

        assertThat(scan(AGENT_OF_1).getIsValid()).isTrue();
        assertThat(ticket.getEventStructureId()).isEqualTo(1L);
    }

    @Test
    void ticketsAndStatisticsAreVisibleToTheOrganizerOnly() {
        assertThat(service.getTicketsByEvent(1L, PageRequest.of(0, 20), STRUCTURE_ADMIN_OF_1)).isNotNull();
        assertThat(service.getEventStatistics(1L, ADMIN)).isNotNull();

        assertThatThrownBy(() -> service.getEventStatistics(1L, STRUCTURE_ADMIN_OF_2))
                .isInstanceOf(TicketAccessDeniedException.class);
        assertThatThrownBy(() -> service.getTicketsByEvent(1L, PageRequest.of(0, 20), AGENT_OF_1))
                .isInstanceOf(TicketAccessDeniedException.class);
    }
}
