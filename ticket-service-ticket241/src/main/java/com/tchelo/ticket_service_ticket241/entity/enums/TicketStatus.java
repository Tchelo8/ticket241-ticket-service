package com.tchelo.ticket_service_ticket241.entity.enums;

public enum TicketStatus {
    PENDING,      // En attente de confirmation
    RESERVED,     // Réservé (10 minutes) après cela le programme bascule le ticket à expiré
    PAID,         // Payé et confirmé
    CANCELLED,    // Annulé
    USED,         // Utilisé (validé à l'entrée lors de l'évènement)
    EXPIRED,      // Réservation du ticket expirée
    REFUNDED,     // Remboursé
    TRANSFERRED   // Transféré à un autre utilisateur
}
