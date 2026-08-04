package com.dorosoft.erp.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void shouldGenerateRequestIdWhenMissingHeader() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        filter.init(new MockFilterConfig());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, immediateChain(request));

        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);

        assertThat(requestId)
                .as("generated request id must use safe prefix")
                .startsWith("req-");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPreserveSafeRequestIdAndRejectUnsafeValue() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        filter.init(new MockFilterConfig());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "bad id with space");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, immediateChain(request));

        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);

        assertThat(requestId)
                .as("unsafe request id must be regenerated")
                .isNotEqualTo("bad id with space");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderLengthExceedsLimit() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        filter.init(new MockFilterConfig());

        String tooLong = "x".repeat(RequestIdFilter.MAX_REQUEST_ID_LENGTH + 1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, tooLong);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, immediateChain(request));

        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);

        assertThat(requestId)
                .as("too long request id must be regenerated")
                .isNotEqualTo(tooLong);
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(requestId);
        assertThat(requestId).hasSizeBetween(1, RequestIdFilter.MAX_REQUEST_ID_LENGTH);
        assertThat(MDC.get("requestId")).isNull();

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldSetRequestIdInMdcDuringRequestProcessing() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        filter.init(new MockFilterConfig());

        String safeRequestId = "safe-request-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, safeRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> assertThat(MDC.get("requestId"))
                .as("request id must be available in MDC during filter chain execution")
                .isEqualTo(safeRequestId));

        assertThat(MDC.get("requestId")).isNull();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldRestorePreviousMdcValueAfterRequestProcessing() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        filter.init(new MockFilterConfig());

        MDC.put("requestId", "outer-request-id");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "inner-request-id");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, (req, res) ->
                    assertThat(MDC.get("requestId")).isEqualTo("inner-request-id"));

            assertThat(MDC.get("requestId")).isEqualTo("outer-request-id");
        } finally {
            MDC.remove("requestId");
        }
    }

    private FilterChain immediateChain(HttpServletRequest request) {
        return new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((jakarta.servlet.http.HttpServletRequest) request));
            }
        };
    }
}
