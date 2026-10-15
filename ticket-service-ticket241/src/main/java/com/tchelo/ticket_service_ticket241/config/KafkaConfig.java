package com.tchelo.ticket_service_ticket241.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

// Avec Spring Boot 4, l'auto-configuration Kafka n'est plus dans spring-kafka (il faudrait le starter
// spring-boot-starter-kafka) : producteur, template et admin sont donc déclarés ici, comme dans event-service.
// Sans eux, ticket-service ne démarrait pas (aucun KafkaTemplate) et les NewTopic ci-dessous n'étaient jamais créés.
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // JSON sans en-tête de type : les consommateurs convertissent vers leurs propres classes
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // Crée au démarrage les topics déclarés en NewTopic (3 partitions) s'ils n'existent pas encore
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(config);
    }


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
