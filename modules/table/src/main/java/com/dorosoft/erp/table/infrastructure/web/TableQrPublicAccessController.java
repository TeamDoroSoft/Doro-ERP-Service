package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableManagementException;
import com.dorosoft.erp.table.application.TableOperationObservability;
import com.dorosoft.erp.table.application.TableQrAccessService;
import com.dorosoft.erp.table.application.dto.QrTableAccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qr/table-access")
class TableQrPublicAccessController {

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";

    private final TableQrAccessService service;
    private final TableQrHostValidator hostValidator;
    private final TableQrPublicRateLimiter rateLimiter;
    private final TableOperationObservability observability;
    private final String tenantId;

    TableQrPublicAccessController(
            TableQrAccessService service,
            TableQrHostValidator hostValidator,
            TableQrPublicRateLimiter rateLimiter,
            TableOperationObservability observability,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) {
        this.service = service;
        this.hostValidator = hostValidator;
        this.rateLimiter = rateLimiter;
        this.observability = observability;
        this.tenantId = tenantId;
    }

    @PostMapping
    ResponseEntity<QrTableAccessResponse> verify(
            @RequestBody(required = false) QrTableAccessRequest request,
            HttpServletRequest servletRequest) {
        try {
            hostValidator.verify(servletRequest);
            rateLimiter.verify(servletRequest);
            String token = request == null ? null : request.token();
            QrTableAccessResponse response = service.verify(token);
            observability.success(
                    "qr.verify",
                    tenantId,
                    null,
                    requestId(servletRequest),
                    "QR_ACCESS",
                    null);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(response);
        } catch (TableManagementException exception) {
            observability.failure(
                    "qr.verify",
                    exception.code().name(),
                    tenantId,
                    null,
                    requestId(servletRequest),
                    "QR_ACCESS",
                    null);
            throw exception;
        }
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String text && !text.isBlank()) {
            return text;
        }
        return "req-" + UUID.randomUUID();
    }

    record QrTableAccessRequest(String token) {}
}
