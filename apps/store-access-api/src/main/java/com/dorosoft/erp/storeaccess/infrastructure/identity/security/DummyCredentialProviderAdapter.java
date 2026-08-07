package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.port.identity.DummyCredentialProvider;
import org.springframework.stereotype.Component;

@Component
class DummyCredentialProviderAdapter implements DummyCredentialProvider {

    private final DummyPasswordHash dummyPasswordHash;

    DummyCredentialProviderAdapter(DummyPasswordHash dummyPasswordHash) {
        this.dummyPasswordHash = dummyPasswordHash;
    }

    @Override
    public String dummyPasswordHash() {
        return dummyPasswordHash.value();
    }
}
