package com.tchelo.ticket_service_ticket241.service;

import com.tchelo.ticket_service_ticket241.entity.Ticket;

import java.util.List;

public interface TicketPdfService {

    // Génère un PDF avec une page par billet (infos de l'événement, titulaire, QR code d'entrée)
    byte[] generateTicketsPdf(List<Ticket> tickets);
}
