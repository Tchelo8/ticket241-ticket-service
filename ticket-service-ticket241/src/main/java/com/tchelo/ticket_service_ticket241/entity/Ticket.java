package com.tchelo.ticket_service_ticket241.entity;


import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import com.tchelo.ticket_service_ticket241.entity.enums.PaymentStatus;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_user_id", columnList = "user_id"),
        @Index(name = "idx_ticket_event_id", columnList = "event_id"),
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_number", columnList = "ticket_number"),
        @Index(name = "idx_ticket_qr_code", columnList = "qr_code"),
        @Index(name = "idx_ticket_order_id", columnList = "order_id"),
        @Index(name = "idx_ticket_expiration", columnList = "expiration_date"),
        @Index(name = "idx_ticket_payment_id", columnList = "payment_id")
})
public class Ticket extends BaseEntity {

    // IDENTIFIANTS PRIMAIRES

    @Column(name = "ticket_number", unique = true, nullable = false, length = 20)
    private String ticketNumber;

    @Column(name = "qr_code", unique = true, nullable = false, columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "qr_code_image_url", length = 500)
    private String qrCodeImageUrl;

    @Column(name = "security_hash", unique = true, nullable = false, length = 64)
    private String securityHash;

    @Column(name = "validation_token", unique = true, length = 100)
    private String validationToken;


    // RÉFÉRENCES DE L'ÉVÉNEMENT

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_uuid", nullable = false, length = 36)
    private String eventUuid;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Column(name = "event_slug", length = 250)
    private String eventSlug;

    @Column(name = "event_start_date", nullable = false)
    private LocalDateTime eventStartDate;

    @Column(name = "event_end_date")
    private LocalDateTime eventEndDate;

    @Column(name = "event_venue_name", length = 200)
    private String eventVenueName;

    @Column(name = "event_venue_city_id")
    private Long eventVenueCityId;

    @Column(name = "event_venue_city_name", length = 100)
    private String eventVenueCityName;

    // Structure organisatrice de l'événement (dénormalisée comme le reste) : seuls ses agents et ses
    // admins peuvent scanner ce billet. Null sur les billets créés avant son ajout → relu auprès d'event-service.
    @Column(name = "event_structure_id")
    private Long eventStructureId;


    // CATÉGORIE DE TICKET DU SHOW


    @Column(name = "ticket_category_id", nullable = false)
    private Long ticketCategoryId;

    @Column(name = "ticket_category_name", nullable = false, length = 100)
    private String ticketCategoryName;

    @Column(name = "ticket_category_color", length = 7)
    private String ticketCategoryColor;


    // PROPRIÉTAIRE DU TICKET


    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_email", length = 100)
    private String userEmail;

    @Column(name = "user_phone", length = 50)
    private String userPhone;

    @Column(name = "user_first_name", length = 100)
    private String userFirstName;

    @Column(name = "user_last_name", length = 100)
    private String userLastName;


    // BÉNÉFICIAIRE DU TICKET (si différent de l'acheteur)


    @Column(name = "beneficiary_first_name", length = 100)
    private String beneficiaryFirstName;

    @Column(name = "beneficiary_last_name", length = 100)
    private String beneficiaryLastName;

    @Column(name = "beneficiary_email", length = 100)
    private String beneficiaryEmail;

    @Column(name = "beneficiary_phone", length = 50)
    private String beneficiaryPhone;


    // PLACEMENT ET SIÈGE

    @Column(name = "seat_number", length = 20)
    private String seatNumber;

    @Column(name = "seat_row", length = 10)
    private String seatRow;

    @Column(name = "seat_section", length = 50)
    private String seatSection;

    @Column(name = "is_seated")
    @Builder.Default
    private Boolean isSeated = false;


    // TARIFICATION

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "service_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(length = 3)
    @Builder.Default
    private String currency = "XAF";


    // CODE PROMO / RÉDUCTION

    @Column(name = "promo_code", length = 50)
    private String promoCode;

    @Column(name = "promo_discount_percent", precision = 5, scale = 2)
    private BigDecimal promoDiscountPercent;

    @Column(name = "promo_discount_amount", precision = 10, scale = 2)
    private BigDecimal promoDiscountAmount;


    // STATUT DU TICKET

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TicketStatus status = TicketStatus.PENDING;

    @Column(name = "previous_status", length = 20)
    @Enumerated(EnumType.STRING)
    private TicketStatus previousStatus;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "status_changed_by", length = 100)
    private String statusChangedBy;

    @Column(name = "status_change_reason", length = 500)
    private String statusChangeReason;


    // DATES IMPORTANTES

    @Column(name = "reservation_date")
    private LocalDateTime reservationDate;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "purchase_date")
    private LocalDateTime purchaseDate;

    @Column(name = "payment_completed_at")
    private LocalDateTime paymentCompletedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "validated_by", length = 100)
    private String validatedBy;

    @Column(name = "validation_location", length = 100)
    private String validationLocation;

    @Column(name = "validation_device", length = 100)
    private String validationDevice;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "transferred_to_user_id", length = 100)
    private String transferredToUserId;

    @Column(name = "transferred_from_user_id", length = 100)
    private String transferredFromUserId;

    @Column(name = "transfer_reason", length = 500)
    private String transferReason;


    // INFORMATIONS DE PAIEMENT

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "payment_method", length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "payment_status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_currency", length = 3)
    private String paymentCurrency;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "payment_fee", precision = 10, scale = 2)
    private BigDecimal paymentFee;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;


    // COMMANDE / GROUPE (Achats groupés)

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "order_total_amount", precision = 10, scale = 2)
    private BigDecimal orderTotalAmount;

    @Column(name = "order_ticket_count")
    private Integer orderTicketCount;

    @Column(name = "is_primary_ticket")
    @Builder.Default
    private Boolean isPrimaryTicket = false;


    // NOTIFICATIONS

    @Column(name = "email_sent")
    @Builder.Default
    private Boolean emailSent = false;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "sms_sent")
    @Builder.Default
    private Boolean smsSent = false;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "reminder_sent")
    @Builder.Default
    private Boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    // OPTIONS ET PRÉFÉRENCES

    @Column(name = "is_gift")
    @Builder.Default
    private Boolean isGift = false;

    @Column(name = "gift_message", columnDefinition = "TEXT")
    private String giftMessage;

    @Column(name = "special_requirements", columnDefinition = "TEXT")
    private String specialRequirements;

    @Column(name = "dietary_restrictions", length = 200)
    private String dietaryRestrictions;


    // CHECK-IN / PRÉSENCE
    @Column(name = "checked_in")
    @Builder.Default
    private Boolean checkedIn = false;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_in_by", length = 100)
    private String checkedInBy;

    @Column(name = "attendance_confirmed")
    @Builder.Default
    private Boolean attendanceConfirmed = false;


    // NOTES ET COMMENTAIRES

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    // ÉVALUATION POST-ÉVÉNEMENT

    @Column
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;


    // FRAUDE ET SÉCURITÉ

    @Column(name = "is_suspicious")
    @Builder.Default
    private Boolean isSuspicious = false;

    @Column(name = "fraud_score", precision = 5, scale = 2)
    private BigDecimal fraudScore;

    @Column(name = "fraud_check_date")
    private LocalDateTime fraudCheckDate;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "geolocation_lat")
    private Double geolocationLat;

    @Column(name = "geolocation_lng")
    private Double geolocationLng;


    // KAFKA / EVENT SOURCING


    @Column(name = "kafka_event_id", length = 100)
    private String kafkaEventId;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "last_kafka_event", length = 50)
    private String lastKafkaEvent;

    @Column(name = "event_version")
    @Builder.Default
    private Integer eventVersion = 1;


    // MÉTADONNÉES ET AUDIT


    @Column(length = 50)
    private String source;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(length = 50)
    private String browser;

    @Column(name = "operating_system", length = 50)
    private String operatingSystem;


    // OPTIMISTIC LOCKING


    @Version
    private Long version;


    // MÉTHODES UTILITAIRES

    @PrePersist
    protected void onCreate() {
        super.isArchived = false;

        if (this.uuid == null || this.uuid.isEmpty()) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }

        if (this.ticketNumber == null) {
            this.ticketNumber = generateTicketNumber();
        }

        if (this.securityHash == null) {
            this.securityHash = generateSecurityHash();
        }

        if (this.reservationDate == null) {
            this.reservationDate = LocalDateTime.now();
        }

        if (this.expirationDate == null && this.status == TicketStatus.RESERVED) {
            this.expirationDate = LocalDateTime.now().plusMinutes(10);
        }
    }

    private String generateTicketNumber() {
        return newTicketNumber();
    }

    // Numéro de ticket au format TKT-YYYY-XXXXXXXXXX (10 caractères aléatoires, sans 0/O/1/I pour
    // éviter les confusions à la lecture). L'ancien format à 6 chiffres finissait par produire des
    // doublons (contrainte unique violée) et se devinait facilement.
    public static String newTicketNumber() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder suffix = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            suffix.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return String.format("TKT-%d-%s", java.time.Year.now().getValue(), suffix);
    }

    // Génère un hash de sécurité SHA-256
    private String generateSecurityHash() {
        try {
            String data = this.uuid + System.currentTimeMillis() + Math.random();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    // Vérifie si la réservation est expirée
    public boolean isReservationExpired() {
        return this.status == TicketStatus.RESERVED
                && this.expirationDate != null
                && LocalDateTime.now().isAfter(this.expirationDate);
    }

    //Vérifie si le ticket est valide pour validation
    public boolean isValidForValidation() {
        return this.status == TicketStatus.PAID
                && !this.isArchived
                && this.eventStartDate.toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    // Vérifie si le ticket peut être annulé
    public boolean canBeCancelled() {
        return (this.status == TicketStatus.RESERVED || this.status == TicketStatus.PAID)
                && !this.isArchived
                && LocalDateTime.now().isBefore(this.eventStartDate);
    }

    // Change le statut du ticket avec audit
    public void changeStatus(TicketStatus newStatus, String changedBy, String reason) {
        this.previousStatus = this.status;
        this.status = newStatus;
        this.statusChangedAt = LocalDateTime.now();
        this.statusChangedBy = changedBy;
        this.statusChangeReason = reason;
    }

    // Confirme l'achat du ticket
    public void confirmPurchase(String paymentId, PaymentMethod method, String provider) {
        this.changeStatus(TicketStatus.PAID, "system", "Paiement confirmé");
        this.purchaseDate = LocalDateTime.now();
        this.paymentCompletedAt = LocalDateTime.now();
        this.paymentId = paymentId;
        this.paymentMethod = method;
        this.paymentProvider = provider;
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.expirationDate = null;
    }

    // Valide le ticket à l'entrée
    public void validate(String validatedBy, String location, String device) {
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException("Seuls les tickets payés peuvent être validés");
        }
        this.changeStatus(TicketStatus.USED, validatedBy, "Validé à l'entrée");
        this.validatedAt = LocalDateTime.now();
        this.validatedBy = validatedBy;
        this.validationLocation = location;
        this.validationDevice = device;
        this.checkedIn = true;
        this.checkedInAt = LocalDateTime.now();
        this.checkedInBy = validatedBy;
    }

    //Annule le ticket
    public void cancel(String cancelledBy, String reason) {
        if (!this.canBeCancelled()) {
            throw new IllegalStateException("Ce ticket ne peut pas être annulé");
        }
        this.changeStatus(TicketStatus.CANCELLED, cancelledBy, reason);
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
    }

    // Rembourse le ticket
    public void refund(BigDecimal amount, String reason) {
        this.changeStatus(TicketStatus.REFUNDED, "system", reason);
        this.refundedAt = LocalDateTime.now();
        this.refundAmount = amount;
        this.refundReason = reason;
        this.paymentStatus = PaymentStatus.REFUNDED;
    }

    // Transfère le ticket à un autre utilisateur. Le billet reste PAID (sinon il ne pourrait plus
    // être validé à l'entrée) ; c'est transferredAt/transferredFromUserId qui gardent la trace.
    // Le QR code doit être régénéré par l'appelant pour que l'ancien propriétaire ne puisse plus l'utiliser.
    public void transfer(String newUserId, String reason) {
        this.transferredFromUserId = this.userId;
        this.transferredToUserId = newUserId;
        this.userId = newUserId;
        this.transferredAt = LocalDateTime.now();
        this.transferReason = reason;
        this.statusChangedAt = LocalDateTime.now();
        this.statusChangedBy = this.transferredFromUserId;
        this.statusChangeReason = "Transféré" + (reason != null ? " : " + reason : "");
    }

    // Marque la réservation comme expirée
    public void expire() {
        this.changeStatus(TicketStatus.EXPIRED, "system", "Réservation expirée");
    }

    // Calcule le prix final
    public void calculateFinalPrice() {
        BigDecimal total = this.basePrice
                .add(this.serviceFee != null ? this.serviceFee : BigDecimal.ZERO)
                .add(this.taxAmount != null ? this.taxAmount : BigDecimal.ZERO)
                .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO);

        this.finalPrice = total.max(BigDecimal.ZERO);
    }
}