package com.dorosoft.erp.platform.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalProblemAdviceTest.ProblemController.class)
@org.springframework.context.annotation.Import(GlobalProblemAdviceTest.ProblemAdviceConfiguration.class)
class GlobalProblemAdviceTest {

    static final String VALID_REQUEST_ID = "valid-req-123";

    @Autowired
    MockMvc mockMvc;

    @Test
    void shouldReturnRFC9457ProblemDetailForValidationError() throws Exception {
        mockMvc.perform(post("/web-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"bad\"}")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").value("요청 값 검증 실패"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").value(VALID_REQUEST_ID))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.detail").value("요청 값이 유효하지 않습니다."));
    }

    @Test
    void shouldGeneralizeUnknownExceptionWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/web-test/unknown")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.requestId").value(VALID_REQUEST_ID))
                .andExpect(jsonPath("$.detail").value("요청을 처리하지 못했습니다."));

        String responseBody = mockMvc.perform(get("/web-test/unknown"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .doesNotContain("IllegalStateException")
                .doesNotContain("secret")
                .doesNotContain("password");
    }

    @Test
    void shouldConvertMalformedJsonToStableProblemContract() throws Exception {
        mockMvc.perform(post("/web-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(VALID_REQUEST_ID))
                .andExpect(jsonPath("$.detail").value("요청 형식이 유효하지 않습니다."));
    }

    @Test
    void requestIdHeaderShouldBeReusedIfSafeAndRegeneratedIfUnsafe() throws Exception {
        mockMvc.perform(get("/web-test/request-id")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).isEqualTo(VALID_REQUEST_ID);
                });

        var invalidResult = mockMvc.perform(get("/web-test/request-id")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, "invalid space"))
                .andExpect(status().isOk())
                .andReturn();
        String headerFromInvalid = invalidResult.getResponse().getHeader(RequestIdFilter.HEADER_REQUEST_ID);
        String bodyFromInvalid = invalidResult.getResponse().getContentAsString();

        assertThat(headerFromInvalid)
                .isNotBlank()
                .isNotEqualTo("invalid space");
        assertThat(headerFromInvalid).isEqualTo(bodyFromInvalid);
    }

    @Test
    void shouldRenderBusinessOwnedProblemCodeWithoutAddingItToPlatformRegistry() throws Exception {
        mockMvc.perform(get("/web-test/business-error")
                        .header(RequestIdFilter.HEADER_REQUEST_ID, VALID_REQUEST_ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:doro-erp:problem:sample-business-conflict"))
                .andExpect(jsonPath("$.title").value("업무 충돌"))
                .andExpect(jsonPath("$.code").value("SAMPLE_BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value(VALID_REQUEST_ID));
    }

    static record ValidationPayload(@NotBlank String username, @Email String email) {
    }

    @Configuration
    static class ProblemAdviceConfiguration {
        @org.springframework.context.annotation.Bean
        ProblemController problemController() {
            return new ProblemController();
        }

        @org.springframework.context.annotation.Bean
        GlobalProblemAdvice globalProblemAdvice() {
            return new GlobalProblemAdvice();
        }

        @org.springframework.context.annotation.Bean
        RequestIdFilter requestIdFilter() {
            return new RequestIdFilter();
        }

        @org.springframework.context.annotation.Bean
        RequestContextListener requestContextListener() {
            return new RequestContextListener();
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class ProblemController {

        @org.springframework.web.bind.annotation.PostMapping("/web-test/validation")
        ProblemDetail validation(@org.springframework.web.bind.annotation.RequestBody @Valid ValidationPayload payload) {
            return null;
        }

        @org.springframework.web.bind.annotation.GetMapping("/web-test/unknown")
        void unknown() {
            throw new IllegalStateException("hidden password=abc123");
        }

        @org.springframework.web.bind.annotation.GetMapping("/web-test/request-id")
        String requestId(jakarta.servlet.http.HttpServletRequest request) {
            return String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID));
        }

        @org.springframework.web.bind.annotation.GetMapping("/web-test/business-error")
        void businessError() {
            throw new ProblemAwareException(SampleBusinessCode.SAMPLE_BUSINESS_CONFLICT, "요청이 현재 상태와 충돌합니다.");
        }
    }

    enum SampleBusinessCode implements ProblemCode {
        SAMPLE_BUSINESS_CONFLICT;

        @Override
        public String code() {
            return name();
        }

        @Override
        public String title() {
            return "업무 충돌";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }
    }
}
