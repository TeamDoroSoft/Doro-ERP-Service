package com.dorosoft.erp.store.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class StoreAuditValueMapperTest {

    private static final StoreProfile BASE =
            new StoreProfile("매장", "주소", "연락처", ZoneId.of("Asia/Seoul"));

    @Test
    void returnsNoChangedFieldsWhenProfilesAreEqual() {
        assertThat(StoreAuditValueMapper.changedFields(BASE, BASE)).isEmpty();
    }

    @Test
    void returnsNameWhenOnlyNameChanged() {
        StoreProfile after = new StoreProfile("새 매장", "주소", "연락처", ZoneId.of("Asia/Seoul"));

        assertThat(StoreAuditValueMapper.changedFields(BASE, after)).containsExactly("name");
    }

    @Test
    void returnsAddressAndContactWithoutTheirValues() {
        StoreProfile after = new StoreProfile("매장", "새 주소", "새 연락처", ZoneId.of("Asia/Seoul"));

        assertThat(StoreAuditValueMapper.changedFields(BASE, after))
                .containsExactly("address", "contact");
    }

    @Test
    void returnsAllChangedFieldsInCanonicalOrder() {
        StoreProfile after =
                new StoreProfile("새 매장", "새 주소", "새 연락처", ZoneId.of("UTC"));

        assertThat(StoreAuditValueMapper.changedFields(BASE, after))
                .containsExactly("name", "address", "contact", "timeZone");
    }

    @Test
    void mapsMissingReasonsToUnspecified() {
        assertThat(StoreAuditValueMapper.reasonCodeOf(closure(null))).isEqualTo("UNSPECIFIED");
        assertThat(StoreAuditValueMapper.reasonCodeOf(closure(""))).isEqualTo("UNSPECIFIED");
        assertThat(StoreAuditValueMapper.reasonCodeOf(closure(" \t\n"))).isEqualTo("UNSPECIFIED");
    }

    @Test
    void mapsFreeTextReasonToOther() {
        assertThat(StoreAuditValueMapper.reasonCodeOf(closure("시설 점검 010-1234-5678")))
                .isEqualTo("OTHER");
    }

    private static TemporaryClosure closure(String reason) {
        return new TemporaryClosure(LocalDate.of(2026, 8, 15), reason);
    }
}
