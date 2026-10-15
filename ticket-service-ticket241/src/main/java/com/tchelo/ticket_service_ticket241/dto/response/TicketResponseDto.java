package com.tchelo.ticket_service_ticket241.dto.response;

import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponseDto {

    // Identifiants
    private Long id;
    private String uuid;
    private String ticketNumber;
    private String qrCode;
    private String qrCodeImageUrl;

    // Événement
    private Long eventId;
    private String eventUuid;
    private String eventName;
    private String eventSlug;
    private LocalDateTime eventStartDate;
    private LocalDateTime eventEndDate;
    private String eventVenueName;
    private String eventVenueCityName;

    // Catégorie
    private Long ticketCategoryId;
    private String ticketCategoryName;
    private String ticketCategoryColor;

    // Propriétaire
    private String userId;
    private String userEmail;
    private String userPhone;
    private String userFirstName;
    private String userLastName;

    // Bénéficiaire
    private String beneficiaryFirstName;
    private String beneficiaryLastName;
    private String beneficiaryEmail;
    private String beneficiaryPhone;

    // Siège
    private String seatNumber;
    private String seatRow;
    private String seatSection;
    private Boolean isSeated;

    // Tarification
    private BigDecimal basePrice;
    private BigDecimal serviceFee;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private String currency;

    // Code promo
    private String promoCode;
    private BigDecimal promoDiscountPercent;
    private BigDecimal promoDiscountAmount;

    // Statut
    private TicketStatus status;
    private TicketStatus previousStatus;
    private LocalDateTime statusChangedAt;

    // Dates importantes
    private LocalDateTime reservationDate;
    private LocalDateTime expirationDate;
    private LocalDateTime purchaseDate;
    private LocalDateTime paymentCompletedAt;
    private LocalDateTime validatedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;

    // Paiement
    private String paymentId;
    private PaymentMethod paymentMethod;
    private String paymentProvider;
    private PaymentStatus paymentStatus;
    private BigDecimal paymentAmount;

    // Commande
    private String orderId;
    private Integer orderTicketCount;
    private Boolean isPrimaryTicket;

    // Notifications
    private Boolean emailSent;
    private Boolean smsSent;
    private Boolean reminderSent;

    // Options
    private Boolean isGift;
    private String giftMessage;

    // Check-in
    private Boolean checkedIn;
    private LocalDateTime checkedInAt;

    // Évaluation
    private Integer rating;
    private String review;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Champs calculés
    private Boolean isExpired;
    private Boolean canBeCancelled;
    private Boolean canBeValidated;
    private Long minutesUntilExpiration;
}
