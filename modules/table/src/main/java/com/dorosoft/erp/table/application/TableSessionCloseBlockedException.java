package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardBlocker;
import java.util.List;
import org.springframework.http.HttpStatus;

public class TableSessionCloseBlockedException extends TableManagementException {

    private final List<CloseGuardBlocker> blockers;

    public TableSessionCloseBlockedException(List<CloseGuardBlocker> blockers) {
        super(
                HttpStatus.CONFLICT,
                TableErrorCode.TABLE_SESSION_CLOSE_BLOCKED,
                "Table session can not be closed.");
        this.blockers = List.copyOf(blockers);
    }

    public List<CloseGuardBlocker> blockers() {
        return blockers;
    }
}
