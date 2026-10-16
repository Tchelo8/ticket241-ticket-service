package com.tchelo.ticket_service_ticket241.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

// Bloque toute écriture (POST, PUT, PATCH, DELETE) d'un compte suspendu ou supprimé.
// Les lectures restent permises : un client supprimé garde l'accès à ses billets déjà payés.
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveAccountInterceptor implements HandlerInterceptor {

    static final String USER_ID_HEADER = "X-User-Id";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AccountStatusChecker accountStatusChecker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String phone = request.getHeader(USER_ID_HEADER);
        if (phone == null || phone.isBlank() || READ_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (accountStatusChecker.isActive(phone)) {
            return true;
        }

        log.warn("[AccountStatus] {} {} refusé : compte {} suspendu ou supprimé",
                request.getMethod(), request.getRequestURI(), phone);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"status\":403,\"error\":\"ACCOUNT_NOT_ACTIVE\","
                        + "\"message\":\"Ce compte est suspendu ou supprimé. Contactez le support Ticket241.\"}");
        return false;
    }
}
