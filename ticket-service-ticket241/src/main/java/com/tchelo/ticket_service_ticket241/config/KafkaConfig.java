package com.tchelo.ticket_service_ticket241.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {


    // Topics publiés par Ticket Service
    @Bean
    public NewTopic ticketReservedTopic() {
        return TopicBuilder.name("ticket-reserved")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketPurchaseRequestedTopic() {
        return TopicBuilder.name("ticket-purchase-requested")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketPaidTopic() {
        return TopicBuilder.name("ticket-paid")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Consommé par notification-service (email de confirmation avec le PDF des billets)
    @Bean
    public NewTopic ticketConfirmedTopic() {
        return TopicBuilder.name("ticket-confirmed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketCancelledTopic() {
        return TopicBuilder.name("ticket-cancelled")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketValidatedTopic() {
        return TopicBuilder.name("ticket-validated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reservationExpiredTopic() {
        return TopicBuilder.name("reservation-expired")
                .partitions(3)
                .replicas(1)
                .build();
    }


    // Topics consommés par Ticket Service

    // Ces topics sont créés par Event Service et Payment Service
    // On les déclare ici pour documentation, mais ce n'est pas obligatoire
}
