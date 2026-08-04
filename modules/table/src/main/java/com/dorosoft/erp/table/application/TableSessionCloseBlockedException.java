package com.dorosoft.erp.table.application;

import com.dorosoft.erp.table.application.TableUsageSessionService.StartSessionContext;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardBlocker;
import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import java.util.List;
import org.springframework.http.HttpStatus;

public class TableSessionCloseBlockedException extends TableManagementException {

    private final List<CloseGuardBlocker> blockers;
    private final TableUsageSessionSnapshot session;
    private final StartSessionContext context;

    public TableSessionCloseBlockedException(
            List<CloseGuardBlocker> blockers,
            TableUsageSessionSnapshot session,
            StartSessionContext context) {
        super(
                HttpStatus.CONFLICT,
                TableErrorCode.TABLE_SESSION_CLOSE_BLOCKED,
                "Table session can not be closed.");
        this.blockers = List.copyOf(blockers);
        this.session = session;
        this.context = context;
    }

    public List<CloseGuardBlocker> blockers() {
        return blockers;
    }

    public TableUsageSessionSnapshot session() {
        return session;
    }

    public StartSessionContext context() {
        return context;
    }
}
