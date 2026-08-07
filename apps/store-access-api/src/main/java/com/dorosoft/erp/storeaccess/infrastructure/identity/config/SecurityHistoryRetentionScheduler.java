package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import com.dorosoft.erp.storeaccess.application.api.identity.SecurityHistoryRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily trigger for {@link SecurityHistoryRetentionService} (ADR-02-015: "매일 제한된 Batch로 삭제"). Each
 * scheduled run drains every logically-expired record accumulated since the previous run, but never in a
 * single unbounded delete: it repeats the Service's short, bounded batch delete until a run deletes nothing
 * more, capped defensively so a misconfigured Clock/backlog cannot spin forever inside one scheduled
 * invocation.
 */
@Component
class SecurityHistoryRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecurityHistoryRetentionScheduler.class);
    private static final int MAX_BATCHES_PER_RUN = 1000;

    private final SecurityHistoryRetentionService retentionService;

    SecurityHistoryRetentionScheduler(SecurityHistoryRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${doro.identity.security-history.retention-cron}")
    void deleteExpiredSecurityHistory() {
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = retentionService.deleteExpiredBatch();
            totalDeleted += deleted;
            if (deleted == 0) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("Deleted {} expired local security history record(s)", totalDeleted);
        }
    }
}
