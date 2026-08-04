package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader;
import org.springframework.stereotype.Repository;

@Repository
class NoopTableSessionCloseGuardReader implements TableSessionCloseGuardReader {

    @Override
    public CloseGuardResult inspect(CloseGuardQuery query) {
        return CloseGuardResult.closable();
    }
}
