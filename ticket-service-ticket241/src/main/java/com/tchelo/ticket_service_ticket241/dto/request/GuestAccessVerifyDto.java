package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestAccessVerifyDto {

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^\\+?[0-9 .\\-()]{8,20}$", message = "Numéro de téléphone invalide")
    private String phone;

    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[0-9]{6}$", message = "Le code contient 6 chiffres")
    private String code;
}
