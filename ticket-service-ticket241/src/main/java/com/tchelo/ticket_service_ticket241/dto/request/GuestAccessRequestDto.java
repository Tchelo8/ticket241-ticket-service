package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Demande d'un code pour retrouver ses billets achetés sans compte
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestAccessRequestDto {

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^\\+?[0-9 .\\-()]{8,20}$", message = "Numéro de téléphone invalide")
    private String phone;
}
