package com.tchelo.ticket_service_ticket241.config;

import com.tchelo.ticket_service_ticket241.security.ActiveAccountInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Toute écriture sur /api/** passe par le contrôle "compte toujours actif" (pas /internal/**)
@Configuration
@RequiredArgsConstructor
public class ActiveAccountWebConfig implements WebMvcConfigurer {

    private final ActiveAccountInterceptor activeAccountInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeAccountInterceptor).addPathPatterns("/api/**");
    }
}
