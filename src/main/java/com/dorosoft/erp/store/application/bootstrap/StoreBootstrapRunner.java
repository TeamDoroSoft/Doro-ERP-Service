package com.dorosoft.erp.store.application.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "doro.store.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StoreBootstrapRunner implements ApplicationRunner {

    private final StoreBootstrapService bootstrapService;

    public StoreBootstrapRunner(StoreBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.bootstrap();
    }
}
