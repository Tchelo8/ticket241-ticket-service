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
public class ValidateTicketDto {

    @NotBlank(message = "Le QR Code est obligatoire")
    private String qrCode;

    @Size(max = 100)
    private String validationLocation;

    @Size(max = 100)
    private String validationDevice;

    private String validatedBy;
}
