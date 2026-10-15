package com.tchelo.ticket_service_ticket241.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelTicketDto {

    @NotBlank(message = "La raison de l'annulation est obligatoire")
    @Size(min = 10, max = 500, message = "La raison doit contenir entre 10 et 500 caractères")
    private String reason;

    private Boolean requestRefund;
}
