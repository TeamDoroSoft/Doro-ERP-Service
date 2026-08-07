package com.dorosoft.erp.storeaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * {@code @SpringBootApplication}'s default component scan only covers {@code com.dorosoft.erp.storeaccess};
 * {@code com.dorosoft.erp.platform.web} (GlobalProblemAdvice, RequestIdFilter) must be scanned explicitly or
 * ProblemAwareException from a Controller falls through to a raw 500 instead of the shared Problem Detail
 * contract.
 *
 * <p>An explicit {@code @ComponentScan} directly on this class replaces — rather than adds to —
 * {@code @SpringBootApplication}'s own implicit scan, so both packages must be listed together here, and
 * {@link TypeExcludeFilter} must be re-added explicitly: it is what keeps one test's
 * {@code @TestConfiguration} classes from bleeding into another test's context, and is otherwise only
 * wired in by {@code @SpringBootApplication}'s composed (now-superseded) scan.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.dorosoft.erp.storeaccess", "com.dorosoft.erp.platform.web"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
public class StoreAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreAccessApplication.class, args);
    }
}
