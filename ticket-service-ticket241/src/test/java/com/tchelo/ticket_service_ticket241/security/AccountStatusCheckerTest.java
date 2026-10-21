package com.tchelo.ticket_service_ticket241.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Appel réel construit vers auth-service (sans réseau) : URL exacte, cache et repli si auth est injoignable
class AccountStatusCheckerTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://auth-service");
    private final MockRestServiceServer authService = MockRestServiceServer.bindTo(builder).build();
    private final AccountStatusChecker checker = new AccountStatusChecker(builder.build(), Clock.systemUTC());

    @Test
    void thePlusOfAnInternationalNumberIsEncoded() {
        // Non encodé, le "+" devenait un espace côté auth-service : compte introuvable, refusé à tort
        authService.expect(requestTo("http://auth-service/internal/accounts/status?phone=%2B24177000000"))
                .andRespond(withSuccess("{\"phone\":\"+24177000000\",\"status\":\"ACTIVE\",\"active\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(checker.isActive("+24177000000")).isTrue();
        authService.verify();
    }

    @Test
    void theAnswerIsCachedAndAnUnreachableAuthServiceLetsWritesThrough() {
        authService.expect(requestTo("http://auth-service/internal/accounts/status?phone=077000000"))
                .andRespond(withSuccess("{\"phone\":\"077000000\",\"status\":\"SUSPENDED\",\"active\":false}",
                        MediaType.APPLICATION_JSON));
        authService.expect(requestTo("http://auth-service/internal/accounts/status?phone=066000000"))
                .andRespond(withServerError());

        assertThat(checker.isActive("077000000")).isFalse();
        assertThat(checker.isActive("077000000")).isFalse(); // depuis le cache : pas de 2e appel
        assertThat(checker.isActive("066000000")).isTrue();
        authService.verify();
    }
}
