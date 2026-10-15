package com.tchelo.ticket_service_ticket241.dto.request;

import com.tchelo.ticket_service_ticket241.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseTicketsDto {

    @NotEmpty(message = "Au moins un ticket doit être spécifié")
    private List<Long> ticketIds;

    @NotNull(message = "La méthode de paiement est obligatoire")
    private PaymentMethod paymentMethod;

    @Size(max = 100)
    private String paymentReference;

    // Informations spécifiques Mobile Money
    @Size(max = 50)
    private String mobileMoneyNumber;

    @Size(max = 10)
    private String mobileMoneyOtp;

    // Informations carte bancaire (si applicable)
    @Size(max = 100)
    private String cardToken;

    // Métadonnées
    private String ipAddress;
    private String userAgent;
}
