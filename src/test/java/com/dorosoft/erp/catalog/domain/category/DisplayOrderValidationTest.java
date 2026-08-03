package com.dorosoft.erp.catalog.domain.category;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DisplayOrderValidation - 정렬 대상 ID 집합 규칙")
class DisplayOrderValidationTest {

    @Test
    @DisplayName("현재 대상과 정확히 같은 집합이면 통과한다")
    void passesWhenSetsMatchExactly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        assertThatCode(() -> DisplayOrderValidation.requireExactIdSet(List.of(a, b, c), List.of(c, a, b)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("요청에 같은 ID가 중복되면 거부한다")
    void rejectsDuplicateInRequest() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThatThrownBy(() -> DisplayOrderValidation.requireExactIdSet(List.of(a, b), List.of(a, a)))
                .isInstanceOf(InvalidDisplayOrderException.class);
    }

    @Test
    @DisplayName("현재 대상 중 하나가 요청에서 누락되면 거부한다")
    void rejectsMissingId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThatThrownBy(() -> DisplayOrderValidation.requireExactIdSet(List.of(a, b), List.of(a)))
                .isInstanceOf(InvalidDisplayOrderException.class);
    }

    @Test
    @DisplayName("현재 대상에 없는 ID(다른 소속)가 섞이면 거부한다")
    void rejectsForeignId() {
        UUID a = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();

        assertThatThrownBy(() -> DisplayOrderValidation.requireExactIdSet(List.of(a), List.of(a, foreign)))
                .isInstanceOf(InvalidDisplayOrderException.class);
    }

    @Test
    @DisplayName("둘 다 비어 있으면 통과한다")
    void passesWhenBothEmpty() {
        assertThatCode(() -> DisplayOrderValidation.requireExactIdSet(List.of(), List.of())).doesNotThrowAnyException();
    }
}
