package com.dorosoft.erp.store.application.settings;

import com.dorosoft.erp.store.application.exception.StoreNotInitializedException;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.presentation.dto.StoreSettingsResponse;
import com.dorosoft.erp.store.presentation.dto.StoreSettingsWebMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetStoreSettingsService {

    private final StoreSettingsRepository repository;

    public GetStoreSettingsService(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StoreSettingsResponse get() {
        return StoreSettingsWebMapper.toResponse(
                repository.findCurrent().orElseThrow(StoreNotInitializedException::new));
    }
}
