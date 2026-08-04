package com.dorosoft.erp.identity.presentation.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiEnvelopeTest {
    @Test
    void envelopeCarriesDataAndThePlatformRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("doro.erp.requestId", "req-1");

        assertThat(ApiEnvelope.of("ok", IdentityRequestId.from(request)))
                .isEqualTo(new ApiEnvelope<>("ok", "req-1"));
    }

    @Test
    void refusesToManufactureASecondRequestId() {
        HttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> IdentityRequestId.from(request))
                .isInstanceOf(IllegalStateException.class);
    }
}
