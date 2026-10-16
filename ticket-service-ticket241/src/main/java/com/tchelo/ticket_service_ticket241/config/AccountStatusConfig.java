package com.tchelo.ticket_service_ticket241.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Client HTTP vers auth-service (statut des comptes). L'intercepteur est branché dans ActiveAccountWebConfig
@Configuration
public class AccountStatusConfig {

    // Client vers les endpoints internes d'auth-service (clé interne partagée INTERNAL_API_KEY)
    @Bean
    public RestClient authServiceRestClient(
            DeferringLoadBalancerInterceptor loadBalancerInterceptor,
            @Value("${ticket.internal.api-key}") String internalApiKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);

        // "auth-service" est résolu via Eureka par l'intercepteur de load-balancing. Pas de bean
        // RestClient.Builder @LoadBalanced : le client Eureka l'utiliserait lui-même (dépendance circulaire)
        return RestClient.builder()
                .baseUrl("http://auth-service")
                .requestInterceptor(loadBalancerInterceptor)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }
}
