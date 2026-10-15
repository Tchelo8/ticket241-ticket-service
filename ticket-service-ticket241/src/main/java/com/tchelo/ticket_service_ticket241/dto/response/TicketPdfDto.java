package com.tchelo.ticket_service_ticket241.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Fichier PDF d'un ou plusieurs billets, prêt à être renvoyé en téléchargement
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPdfDto {

    private String fileName;
    private byte[] content;
}
