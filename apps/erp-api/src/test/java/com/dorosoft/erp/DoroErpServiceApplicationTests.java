package com.dorosoft.erp;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DoroErpServiceApplicationTests {

    @Autowired
    Flyway flyway;

    @Test
    void contextLoadsWithSharedFlywayClasspathLocation() {
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Location::getDescriptor)
                .containsExactly("classpath:db/migration");
        assertThat(flyway.info().pending()).isEmpty();
    }

}
