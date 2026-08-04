package com.dorosoft.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.dorosoft.erp")
public class DoroErpServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoroErpServiceApplication.class, args);
    }

}
