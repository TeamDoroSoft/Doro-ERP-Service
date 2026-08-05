package com.dorosoft.erp.config;

import com.dorosoft.erp.table.application.idempotency.UnavailableTableIdempotencyResponseCrypto;
import com.dorosoft.erp.table.application.port.TableIdempotencyResponseCrypto;
import com.dorosoft.erp.table.infrastructure.persistence.crypto.AesGcmTableIdempotencyResponseCrypto;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class TableRuntimeConfiguration {

    @Bean
    TableIdempotencyResponseCrypto tableIdempotencyResponseCrypto(TableSecurityProperties properties) {
        TableSecurityProperties.Idempotency idempotency = properties.getIdempotency();
        if (!StringUtils.hasText(idempotency.getEncryptionKeyBase64())) {
            return new UnavailableTableIdempotencyResponseCrypto();
        }
        return new AesGcmTableIdempotencyResponseCrypto(
                Base64.getDecoder().decode(idempotency.getEncryptionKeyBase64()));
    }
}
