package com.dorosoft.erp.platform.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailFactoryTest {

    @Test
    void createsDoroProblemAndRequestUrnsWithExtensionProperties() {
        List<FieldError> fieldErrors = List.of(new FieldError("name", "NOT_BLANK"));

        ProblemDetail result = ProblemDetailFactory.create(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "invalid", "req-123", fieldErrors);

        assertThat(result.getType().toString()).isEqualTo("urn:doro-erp:problem:validation-failed");
        assertThat(result.getInstance().toString()).isEqualTo("urn:doro-erp:request:req-123");
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("requestId", "req-123")
                .containsEntry("fieldErrors", fieldErrors);
    }
}
