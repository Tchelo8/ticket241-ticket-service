package com.tchelo.ticket_service_ticket241.dto.request;

import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Paiement d'une commande invité : toujours par Mobile Money depuis le numéro de la commande
// (c'est ce paiement qui prouve que l'invité possède bien ce numéro)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestPurchaseDto {

    @NotNull(message = "La méthode de paiement est obligatoire (AIRTEL_MONEY ou MOOV_MONEY)")
    private PaymentMethod paymentMethod;
}
