package com.tchelo.ticket_service_ticket241.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Demande à auth-service si le compte derrière X-User-Id est toujours actif : un JWT reste valable
// jusqu'à son expiration, mais une suspension ou une suppression doit bloquer les écritures tout de suite.
// Réponse gardée 30 s en cache pour ne pas appeler auth-service à chaque requête.
@Slf4j
@Component
public class AccountStatusChecker {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final RestClient authServiceRestClient;
    private final Clock clock;
    private final Map<String, CachedStatus> cache = new ConcurrentHashMap<>();

    @Autowired
    public AccountStatusChecker(@Qualifier("authServiceRestClient") RestClient authServiceRestClient) {
        this(authServiceRestClient, Clock.systemUTC());
    }

    AccountStatusChecker(RestClient authServiceRestClient, Clock clock) {
        this.authServiceRestClient = authServiceRestClient;
        this.clock = clock;
    }

    public boolean isActive(String phone) {
        Instant now = clock.instant();
        CachedStatus cached = cache.get(phone);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.active();
        }

        try {
            // Numéro passé en variable d'URI : il est alors entièrement encodé ("+241…" → "%2B241…").
            // Mis tel quel dans la requête, le "+" était lu comme un espace par auth-service : compte
            // introuvable, donc traité comme supprimé (403 ACCOUNT_NOT_ACTIVE à tort)
            AccountStatusResponse response = authServiceRestClient.get()
                    .uri(uri -> uri.path("/internal/accounts/status").queryParam("phone", "{phone}").build(phone))
                    .retrieve()
                    .body(AccountStatusResponse.class);
            boolean active = response != null && response.active();
            cache.put(phone, new CachedStatus(active, now.plus(CACHE_TTL)));
            return active;
        } catch (Exception e) {
            // auth-service injoignable : on laisse passer (le JWT a déjà été validé par la gateway)
            // plutôt que de bloquer toute la plateforme ; la dernière réponse connue reste prioritaire
            log.warn("[AccountStatus] auth-service injoignable, statut de {} non vérifié : {}", phone, e.getMessage());
            return cached == null || cached.active();
        }
    }

    record AccountStatusResponse(String phone, String status, boolean active) {
    }

    private record CachedStatus(boolean active, Instant expiresAt) {
    }
}
