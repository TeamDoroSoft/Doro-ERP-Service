package com.dorosoft.erp.platform.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void handlesApiException() throws Exception {
        mockMvc.perform(get("/test-errors/api"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TEST_ERROR"))
                .andExpect(jsonPath("$.detail").value("테스트 오류"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, org.hamcrest.Matchers.startsWith("req-")));
    }

    @Test
    void handlesMethodArgumentNotValidException() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
    }

    @Test
    void handlesOptimisticLockingFailureException() throws Exception {
        assertProblem("/test-errors/optimistic-lock", 409, "VERSION_CONFLICT");
    }

    @Test
    void handlesAccessDeniedException() throws Exception {
        assertProblem("/test-errors/denied", 403, "FORBIDDEN");
    }

    @Test
    void handlesUnexpectedExceptionWithoutLeakingExceptionDetails() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value("일시적인 오류가 발생했습니다"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret cause"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalArgumentException"))));
    }

    private void assertProblem(String path, int expectedStatus, String expectedCode) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @RestController
    @RequestMapping("/test-errors")
    static class ExceptionTestController {

        @GetMapping("/api")
        void apiException() {
            throw new TestApiException();
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new OptimisticLockingFailureException("conflict");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalArgumentException("secret cause");
        }
    }

    record TestRequest(@NotBlank String name) {
    }

    static class TestApiException extends ApiException {

        @Override
        public String code() {
            return "TEST_ERROR";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.UNPROCESSABLE_CONTENT;
        }

        @Override
        public String detail() {
            return "테스트 오류";
        }
    }
}
