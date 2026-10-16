package com.tchelo.ticket_service_ticket241.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Volontairement sans @Configuration : appliquée seulement aux clients Feign qui la déclarent
// (endpoints internes), pas aux appels vers event-service
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor internalApiKeyInterceptor(@Value("${ticket.internal.api-key}") String internalApiKey) {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }
}
