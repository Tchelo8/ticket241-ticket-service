package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveTicketsDto {

    @NotNull(message = "Le numéro de l'événement est obligatoire")
    private Long eventId;

    @NotNull(message = "Le numéro de la catégorie de ticket est obligatoire")
    private Long ticketCategoryId;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être d'au moins 1 ticket")
    @Max(value = 10, message = "Vous ne pouvez pas réserver plus de 10 tickets à la fois")
    private Integer quantity;

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
