package com.tchelo.ticket_service_ticket241.exception;

// Accès à un billet (et donc à son QR code) par quelqu'un qui n'en est ni le propriétaire ni un administrateur
public class TicketAccessDeniedException extends RuntimeException {
    public TicketAccessDeniedException(String message) {
        super(message);
    }
}
