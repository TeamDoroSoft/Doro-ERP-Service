package com.dorosoft.erp.catalog.domain.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdempotencyRequestHash - 생성 API 공통 멱등성 비교용 요청 Hash")
class IdempotencyRequestHashTest {

    @Test
    @DisplayName("같은 입력값은 항상 같은 Hash를 만든다")
    void sameInputProducesSameHash() {
        assertThat(IdempotencyRequestHash.of("커피", 4500L)).isEqualTo(IdempotencyRequestHash.of("커피", 4500L));
    }

    @Test
    @DisplayName("필드 하나만 달라도 다른 Hash를 만든다")
    void differentFieldProducesDifferentHash() {
        assertThat(IdempotencyRequestHash.of("커피", 4500L)).isNotEqualTo(IdempotencyRequestHash.of("커피", 5000L));
    }

    @Test
    @DisplayName("null 필드도 안정적으로 다룬다")
    void handlesNullFieldsStably() {
        assertThat(IdempotencyRequestHash.of("이름", null)).isEqualTo(IdempotencyRequestHash.of("이름", null));
        assertThat(IdempotencyRequestHash.of("이름", null)).isNotEqualTo(IdempotencyRequestHash.of("이름", "값"));
    }
}
