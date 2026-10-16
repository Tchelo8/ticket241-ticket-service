package com.tchelo.ticket_service_ticket241.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveTicketsDto {

    @NotNull(message = "Le numéro de l'événement est obligatoire")
    private Long eventId;

    public static final int MAX_TICKETS_PER_ORDER = 10;

    // Panier : une ligne par catégorie (ex. 2 Standard + 1 VIP), le tout dans UNE commande et payé en une fois
    @Valid
    @Size(max = 20, message = "Trop de lignes dans la commande")
    private List<ReservationItemDto> items;

    // Ancien format, une seule catégorie : utilisé seulement si items est vide
    private Long ticketCategoryId;

    @Min(value = 1, message = "La quantité doit être d'au moins 1 ticket")
    private Integer quantity;

    // Lignes de la commande, catégories en double regroupées
    public List<ReservationItemDto> resolvedItems() {
        return ReservationItems.resolve(items, ticketCategoryId, quantity);
    }

    @JsonIgnore
    @AssertTrue(message = "Choisissez au moins une catégorie de billet")
    public boolean isItemsPresent() {
        return !resolvedItems().isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "Vous ne pouvez pas réserver plus de 10 tickets à la fois")
    public boolean isTotalQuantityAllowed() {
        return ReservationItems.total(resolvedItems()) <= MAX_TICKETS_PER_ORDER;
    }

    @Email(message = "Email invalide, vérifiez votre email et réesayez.")
    private String userEmail;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Numéro de téléphone invalide")
    private String userPhone;

    @Size(max = 100)
    private String userFirstName;

    @Size(max = 100)
    private String userLastName;

    // Informations bénéficiaire (si différent de l'acheteur)
    @Size(max = 100)
    private String beneficiaryFirstName;

    @Size(max = 100)
    private String beneficiaryLastName;

    @Email(message = "Email bénéficiaire invalide")
    private String beneficiaryEmail;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Téléphone bénéficiaire invalide")
    private String beneficiaryPhone;

    // Options
    private Boolean isGift;

    @Size(max = 500)
    private String giftMessage;

    @Size(max = 1000)
    private String specialRequirements;

    // Code promo
    @Size(max = 50)
    private String promoCode;

    // Métadonnées
    private String source;
    private String deviceType;
    private String browser;
    private String operatingSystem;
    private String ipAddress;
    private Double geolocationLat;
    private Double geolocationLng;
}
