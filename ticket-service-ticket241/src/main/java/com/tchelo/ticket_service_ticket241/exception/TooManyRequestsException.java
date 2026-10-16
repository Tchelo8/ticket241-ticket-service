package com.tchelo.ticket_service_ticket241.exception;

// Trop d'appels sur une route invité (réservation, demande de code…) : 429
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
