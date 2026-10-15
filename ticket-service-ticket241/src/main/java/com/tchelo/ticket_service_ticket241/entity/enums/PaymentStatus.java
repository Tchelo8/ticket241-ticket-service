package com.tchelo.ticket_service_ticket241.entity.enums;

public enum PaymentStatus {
    PENDING,      // En attente
    PROCESSING,   // En cours de traitement
    COMPLETED,    // Terminé avec succès
    FAILED,       // Échoué
    REFUNDED,     // Remboursé
    CANCELLED     // Annulé
}
