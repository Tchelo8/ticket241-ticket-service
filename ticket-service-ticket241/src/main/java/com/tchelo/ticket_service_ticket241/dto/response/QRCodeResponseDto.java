package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QRCodeResponseDto {

    private String ticketUuid;
    private String ticketNumber;
    private String qrCode;
    private String qrCodeImageUrl;
    private String qrCodeBase64;
    private String eventName;
    private String message;
}
