package com.tchelo.ticket_service_ticket241.client;

import com.tchelo.ticket_service_ticket241.client.dto.SimpleEmailRequestDto;
import com.tchelo.ticket_service_ticket241.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Endpoints internes de notification-service (clé interne ajoutée par InternalFeignConfig)
@FeignClient(name = "notification-service-ticket241", contextId = "notificationServiceClient",
        configuration = InternalFeignConfig.class)
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/internal/email")
    void sendEmail(@RequestBody SimpleEmailRequestDto request);
}
