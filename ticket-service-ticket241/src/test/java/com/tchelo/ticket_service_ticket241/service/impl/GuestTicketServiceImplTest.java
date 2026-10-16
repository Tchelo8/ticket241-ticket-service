package com.tchelo.ticket_service_ticket241.service.impl;

import com.tchelo.ticket_service_ticket241.client.NotificationServiceClient;
import com.tchelo.ticket_service_ticket241.client.dto.SimpleEmailRequestDto;
import com.tchelo.ticket_service_ticket241.dto.request.*;
import com.tchelo.ticket_service_ticket241.dto.response.GuestAccessSessionDto;
import com.tchelo.ticket_service_ticket241.dto.response.GuestReservationResponseDto;
import com.tchelo.ticket_service_ticket241.dto.response.ReservationResponseDto;
import com.tchelo.ticket_service_ticket241.entity.GuestAccessCode;
import com.tchelo.ticket_service_ticket241.entity.GuestAccessSession;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import com.tchelo.ticket_service_ticket241.exception.InvalidTicketOperationException;
import com.tchelo.ticket_service_ticket241.exception.TicketAccessDeniedException;
import com.tchelo.ticket_service_ticket241.exception.TooManyRequestsException;
import com.tchelo.ticket_service_ticket241.mapper.TicketMapper;
import com.tchelo.ticket_service_ticket241.repository.GuestAccessCodeRepository;
import com.tchelo.ticket_service_ticket241.repository.GuestAccessSessionRepository;
import com.tchelo.ticket_service_ticket241.repository.TicketRepository;
import com.tchelo.ticket_service_ticket241.security.GuestRateLimiter;
import com.tchelo.ticket_service_ticket241.security.GuestTokens;
import com.tchelo.ticket_service_ticket241.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Achat sans compte : accès par le jeton de la commande ou par code, paiement depuis le numéro de la
// commande, limites anti-abus, rattachement à un compte
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestTicketServiceImplTest {

    private static final String ORDER_ID = "order-1";
    private static final String PHONE = "+24177123456";
    private static final String GUEST_USER = "guest:" + PHONE;

    @Mock private TicketService ticketService;
    @Mock private TicketRepository ticketRepository;
    @Mock private GuestAccessCodeRepository accessCodeRepository;
    @Mock private GuestAccessSessionRepository accessSessionRepository;
    @Mock private NotificationServiceClient notificationServiceClient;

    private GuestTicketServiceImpl service;
    private final List<Ticket> orderTickets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new GuestTicketServiceImpl(ticketService, ticketRepository, new TicketMapper(),
                accessCodeRepository, accessSessionRepository, notificationServiceClient, new GuestRateLimiter());

        orderTickets.add(ticket(1L, TicketStatus.RESERVED));
        orderTickets.add(ticket(2L, TicketStatus.RESERVED));
        when(ticketRepository.findByOrderIdAndIsArchivedFalse(ORDER_ID)).thenReturn(orderTickets);
        when(ticketService.reserveTickets(any(ReserveTicketsDto.class), anyString()))
                .thenReturn(ReservationResponseDto.builder().orderId(ORDER_ID).build());
        when(accessCodeRepository.save(any(GuestAccessCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accessSessionRepository.save(any(GuestAccessSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aGuestReservationIsOwnedByAGuestIdAndOnlyTheTokenHashIsStored() {
        GuestReservationResponseDto response = service.reserve(reserveRequest("077 12 34 56"), "10.0.0.1");

        ArgumentCaptor<ReserveTicketsDto> reservation = ArgumentCaptor.forClass(ReserveTicketsDto.class);
        verify(ticketService).reserveTickets(reservation.capture(), eq("guest:07712345" + "6"));
        assertThat(reservation.getValue().getUserPhone()).isEqualTo("077123456");

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(orderTickets).allSatisfy(ticket -> {
            assertThat(ticket.getGuestOrder()).isTrue();
            assertThat(ticket.getGuestAccessTokenHash()).isEqualTo(GuestTokens.sha256(response.getAccessToken()));
            assertThat(ticket.getGuestAccessTokenHash()).isNotEqualTo(response.getAccessToken());
        });
    }

    @Test
    void aPhoneCannotHoldMoreThanTwoUnpaidGuestOrders() {
        when(ticketRepository.countOrdersByUserIdAndStatusIn(eq("guest:077123456"), anyList())).thenReturn(2L);

        assertThatThrownBy(() -> service.reserve(reserveRequest("077123456"), "10.0.0.1"))
                .isInstanceOf(InvalidTicketOperationException.class);
        verify(ticketService, never()).reserveTickets(any(), anyString());
    }

    @Test
    void reservationsAreRateLimitedPerConnection() {
        for (int i = 0; i < 10; i++) {
            service.reserve(reserveRequest("077123456"), "10.0.0.9");
        }
        assertThatThrownBy(() -> service.reserve(reserveRequest("077123456"), "10.0.0.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void anOrderIsOnlyReadableWithItsToken() {
        markAsGuestOrder("le-bon-jeton");

        assertThat(service.getOrder(ORDER_ID, "le-bon-jeton")).hasSize(2);
        assertThatThrownBy(() -> service.getOrder(ORDER_ID, "un-autre-jeton"))
                .isInstanceOf(TicketAccessDeniedException.class);
        assertThatThrownBy(() -> service.getOrder(ORDER_ID, null))
                .isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    void anAccountOrderIsNeverReachableThroughTheGuestRoutes() {
        markAsGuestOrder("jeton");
        orderTickets.forEach(ticket -> ticket.setGuestOrder(false));

        assertThatThrownBy(() -> service.getOrder(ORDER_ID, "jeton")).isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    void theGuestPaysFromThePhoneOfTheOrderByMobileMoneyOnly() {
        markAsGuestOrder("jeton");

        assertThatThrownBy(() -> service.purchase(ORDER_ID, "jeton", new GuestPurchaseDto(PaymentMethod.CARD)))
                .isInstanceOf(InvalidTicketOperationException.class);

        service.purchase(ORDER_ID, "jeton", new GuestPurchaseDto(PaymentMethod.AIRTEL_MONEY));
        ArgumentCaptor<PurchaseTicketsDto> purchase = ArgumentCaptor.forClass(PurchaseTicketsDto.class);
        verify(ticketService).purchaseTickets(purchase.capture(), eq(GUEST_USER));
        assertThat(purchase.getValue().getMobileMoneyNumber()).isEqualTo(PHONE);
        assertThat(purchase.getValue().getTicketIds()).containsExactly(1L, 2L);
    }

    @Test
    void noCodeIsSentWhenThePhoneHasNoPaidOrderWithAnEmail() {
        when(ticketRepository.findByUserIdAndStatusInAndIsArchivedFalse(eq(GUEST_USER), anyList())).thenReturn(List.of());

        assertThat(service.requestAccessCode(new GuestAccessRequestDto(PHONE), "10.0.0.1").get("message")).isNotBlank();
        verify(notificationServiceClient, never()).sendEmail(any());
        verify(accessCodeRepository, never()).save(any());
    }

    @Test
    void aValidCodeOpensASessionToThePaidTicketsOfThePhone() {
        String code = requestCodeSentTo("awa@test.com");

        GuestAccessSessionDto session = service.verifyAccessCode(new GuestAccessVerifyDto(PHONE, code), "10.0.0.1");
        assertThat(session.getSessionToken()).isNotBlank();

        ArgumentCaptor<GuestAccessSession> saved = ArgumentCaptor.forClass(GuestAccessSession.class);
        verify(accessSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(GuestTokens.sha256(session.getSessionToken()));
    }

    @Test
    void aWrongCodeCountsAsAnAttemptAndFiveAttemptsBurnTheCode() {
        GuestAccessCode stored = storedCodeAfterRequest();

        for (int i = 0; i < GuestAccessCode.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verifyAccessCode(new GuestAccessVerifyDto(PHONE, "000000"), "10.0.0.1"))
                    .isInstanceOf(InvalidTicketOperationException.class);
        }
        assertThat(stored.getAttempts()).isEqualTo(GuestAccessCode.MAX_ATTEMPTS);
        assertThat(stored.isUsable(LocalDateTime.now())).isFalse();
    }

    @Test
    void anUnknownOrExpiredSessionIsRefused() {
        when(accessSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(GuestAccessSession.builder()
                .phone(PHONE).tokenHash("x").expiresAt(LocalDateTime.now().minusMinutes(1)).build()));

        assertThatThrownBy(() -> service.getSessionTickets("vieille-session")).isInstanceOf(TicketAccessDeniedException.class);
        assertThatThrownBy(() -> service.getSessionTickets(null)).isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    void claimingAnOrderMovesItToTheAccountAndKillsTheGuestLink() {
        markAsGuestOrder("jeton");

        service.claimOrder(ORDER_ID, "jeton", "+24166000000");

        assertThat(orderTickets).allSatisfy(ticket -> {
            assertThat(ticket.getUserId()).isEqualTo("+24166000000");
            assertThat(ticket.getTransferredFromUserId()).isEqualTo(GUEST_USER);
            assertThat(ticket.getGuestOrder()).isFalse();
            assertThat(ticket.getGuestAccessTokenHash()).isNull();
        });
        assertThatThrownBy(() -> service.getOrder(ORDER_ID, "jeton")).isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    void phonesTypedDifferentlyGiveTheSameGuest() {
        assertThat(GuestTokens.normalizePhone("077 12-34.56")).isEqualTo("077123456");
        assertThat(GuestTokens.normalizePhone("0024177123456")).isEqualTo("+24177123456");
    }

    // ===== Utilitaires =====

    private String requestCodeSentTo(String email) {
        storedCodeAfterRequest(email);
        ArgumentCaptor<SimpleEmailRequestDto> mail = ArgumentCaptor.forClass(SimpleEmailRequestDto.class);
        verify(notificationServiceClient).sendEmail(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo(email);
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(mail.getValue().body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private GuestAccessCode storedCodeAfterRequest() {
        return storedCodeAfterRequest("awa@test.com");
    }

    private GuestAccessCode storedCodeAfterRequest(String email) {
        Ticket paid = ticket(9L, TicketStatus.PAID);
        paid.setUserEmail(email);
        when(ticketRepository.findByUserIdAndStatusInAndIsArchivedFalse(eq(GUEST_USER), anyList())).thenReturn(List.of(paid));

        service.requestAccessCode(new GuestAccessRequestDto(PHONE), "10.0.0.1");

        ArgumentCaptor<GuestAccessCode> saved = ArgumentCaptor.forClass(GuestAccessCode.class);
        verify(accessCodeRepository).save(saved.capture());
        GuestAccessCode code = saved.getValue();
        when(accessCodeRepository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(code));
        return code;
    }

    private static GuestReserveTicketsDto reserveRequest(String phone) {
        return GuestReserveTicketsDto.builder()
                .eventId(1L).ticketCategoryId(10L).quantity(2)
                .phone(phone).email("awa@test.com").firstName("Awa")
                .build();
    }

    private void markAsGuestOrder(String token) {
        orderTickets.forEach(ticket -> {
            ticket.setGuestOrder(true);
            ticket.setGuestAccessTokenHash(GuestTokens.sha256(token));
        });
    }

    private static Ticket ticket(Long id, TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-" + id).eventId(1L).eventName("Concert")
                .eventStartDate(LocalDateTime.now().plusDays(5))
                .ticketCategoryId(10L).ticketCategoryName("VIP")
                .userId(GUEST_USER).userPhone(PHONE)
                .basePrice(new BigDecimal("10000")).finalPrice(new BigDecimal("10000"))
                .orderId(ORDER_ID).status(status)
                .build();
        ticket.setId(id);
        ticket.setUuid("uuid-" + id);
        ticket.setCreatedAt(LocalDateTime.now());
        return ticket;
    }
}
