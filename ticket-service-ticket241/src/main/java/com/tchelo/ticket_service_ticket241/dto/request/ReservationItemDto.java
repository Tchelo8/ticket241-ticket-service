package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Une ligne du panier : X billets d'une catégorie (une commande peut en contenir plusieurs)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationItemDto {

    @NotNull(message = "La catégorie de billet est obligatoire")
    private Long ticketCategoryId;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être d'au moins 1 ticket")
    private Integer quantity;
}
