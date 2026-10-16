package com.tchelo.ticket_service_ticket241.security;

import com.tchelo.ticket_service_ticket241.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Limite le nombre d'appels des routes invités (sans compte, donc sans autre frein) par clé
// (adresse IP, téléphone…) sur une fenêtre glissante. En mémoire : suffisant pour une instance ;
// à remplacer par Redis le jour où ticket-service tourne en plusieurs exemplaires.
@Component
public class GuestRateLimiter {

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public GuestRateLimiter() {
        this(Clock.systemUTC());
    }

    GuestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key, int maxHits, Duration window, String message) {
        Instant now = clock.instant();
        Deque<Instant> keyHits = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (keyHits) {
            while (!keyHits.isEmpty() && !keyHits.peekFirst().isAfter(now.minus(window))) {
                keyHits.pollFirst();
            }
            if (keyHits.size() >= maxHits) {
                throw new TooManyRequestsException(message);
            }
            keyHits.addLast(now);
        }
    }

    // Les fenêtres font au plus 1 h : on oublie les clés sans appel depuis 2 h
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void forgetIdleKeys() {
        Instant threshold = clock.instant().minus(Duration.ofHours(2));
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Instant last = entry.getValue().peekLast();
                return last == null || last.isBefore(threshold);
            }
        });
    }
}
