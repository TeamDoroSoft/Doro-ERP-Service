package com.dorosoft.erp.catalog.application.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "doro.catalog.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CatalogBootstrapRunner implements ApplicationRunner {

    private final CatalogBootstrapService bootstrapService;

    public CatalogBootstrapRunner(CatalogBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.bootstrap();
    }
}
