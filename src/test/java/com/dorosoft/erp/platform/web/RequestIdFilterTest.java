package com.dorosoft.erp.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    @Test
    void addsRequestIdToAttributeHeaderAndMdcDuringRequest() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                mdcRequestId.set(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)));

        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(requestId).startsWith("req-");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(requestId);
        assertThat(mdcRequestId.get()).isEqualTo(requestId);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }
}
