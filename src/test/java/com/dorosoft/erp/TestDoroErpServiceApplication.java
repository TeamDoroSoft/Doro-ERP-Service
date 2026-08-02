package com.dorosoft.erp;

import org.springframework.boot.SpringApplication;

public class TestDoroErpServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(DoroErpServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
