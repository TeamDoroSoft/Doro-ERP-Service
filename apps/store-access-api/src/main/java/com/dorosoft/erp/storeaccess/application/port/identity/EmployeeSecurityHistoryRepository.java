package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeSecurityHistoryRepository {

    Optional<EmployeeSecurityHistory> findById(UUID id);

    EmployeeSecurityHistory save(EmployeeSecurityHistory history);
}
