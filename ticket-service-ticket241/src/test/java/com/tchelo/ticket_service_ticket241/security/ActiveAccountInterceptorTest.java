package com.tchelo.ticket_service_ticket241.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Un compte suspendu ou supprimé ne peut plus écrire, même avec un JWT encore valide
class ActiveAccountInterceptorTest {

    private final AccountStatusChecker checker = mock(AccountStatusChecker.class);
    private final ActiveAccountInterceptor interceptor = new ActiveAccountInterceptor(checker);

    @Test
    void writesOfAnInactiveAccountAreRefused() throws Exception {
        when(checker.isActive("+24160000001")).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("POST", "+24160000001"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void writesOfAnActiveAccountGoThrough() throws Exception {
        when(checker.isActive("+24160000001")).thenReturn(true);

        assertThat(interceptor.preHandle(request("DELETE", "+24160000001"), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void readsAndAnonymousCallsAreNotChecked() throws Exception {
        assertThat(interceptor.preHandle(request("GET", "+24160000001"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("POST", null), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verify(checker, never()).isActive(anyString());
    }

    private static MockHttpServletRequest request(String method, String phone) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/test");
        if (phone != null) {
            request.addHeader(ActiveAccountInterceptor.USER_ID_HEADER, phone);
        }
        return request;
    }
}
