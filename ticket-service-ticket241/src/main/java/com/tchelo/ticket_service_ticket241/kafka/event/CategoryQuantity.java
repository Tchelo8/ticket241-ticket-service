package com.tchelo.ticket_service_ticket241.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Nombre de billets d'une catégorie dans une commande (ticket-reserved, ticket-paid).
// Même forme dans ticket-service et event-service : c'est le contrat JSON des messages.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryQuantity {

    private Long ticketCategoryId;
    private Integer quantity;
}
