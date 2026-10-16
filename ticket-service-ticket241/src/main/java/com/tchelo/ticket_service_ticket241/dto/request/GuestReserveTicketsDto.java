package com.tchelo.ticket_service_ticket241.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Réservation sans compte : le téléphone est obligatoire (c'est lui qui paiera par Mobile Money),
// l'email est facultatif (il sert à recevoir les billets en PDF et à les retrouver plus tard)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestReserveTicketsDto {

    public static final int MAX_GUEST_TICKETS_PER_ORDER = 5;

    @NotNull(message = "Le numéro de l'événement est obligatoire")
    private Long eventId;

    // Panier : une ligne par catégorie, le tout dans une commande payée en une fois
    @Valid
    @Size(max = 20, message = "Trop de lignes dans la commande")
    private List<ReservationItemDto> items;

    // Ancien format, une seule catégorie : utilisé seulement si items est vide
    private Long ticketCategoryId;

    @Min(value = 1, message = "La quantité doit être d'au moins 1 ticket")
    private Integer quantity;

    public List<ReservationItemDto> resolvedItems() {
        return ReservationItems.resolve(items, ticketCategoryId, quantity);
    }

    @JsonIgnore
    @AssertTrue(message = "Choisissez au moins une catégorie de billet")
    public boolean isItemsPresent() {
        return !resolvedItems().isEmpty();
    }

    // 5 billets au total, toutes catégories confondues
    @JsonIgnore
    @AssertTrue(message = "Sans compte, vous pouvez acheter au maximum 5 billets par commande")
    public boolean isTotalQuantityAllowed() {
        return ReservationItems.total(resolvedItems()) <= MAX_GUEST_TICKETS_PER_ORDER;
    }

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^\\+?[0-9 .\\-()]{8,20}$", message = "Numéro de téléphone invalide")
    private String phone;

    @Email(message = "Email invalide, vérifiez votre email et réessayez.")
    @Size(max = 100)
    private String email;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    private String source;
}
