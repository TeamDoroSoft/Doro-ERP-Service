package com.dorosoft.erp.catalog.application.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료 PENDING Media 정리 주기 실행. 테스트·운영 도구는 MediaExpirySweepService를 직접 호출한다. */
@Component
@ConditionalOnProperty(name = "doro.catalog.media.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MediaExpirySweepScheduler {

    private final MediaExpirySweepService sweepService;

    public MediaExpirySweepScheduler(MediaExpirySweepService sweepService) {
        this.sweepService = sweepService;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void sweep() {
        sweepService.rejectExpiredPending();
    }
}
