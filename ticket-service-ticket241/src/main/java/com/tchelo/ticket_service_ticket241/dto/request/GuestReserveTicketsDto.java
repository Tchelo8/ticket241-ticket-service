package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @NotNull(message = "Le numéro de la catégorie de ticket est obligatoire")
    private Long ticketCategoryId;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être d'au moins 1 ticket")
    @Max(value = MAX_GUEST_TICKETS_PER_ORDER,
            message = "Sans compte, vous pouvez acheter au maximum 5 billets par commande")
    private Integer quantity;

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
