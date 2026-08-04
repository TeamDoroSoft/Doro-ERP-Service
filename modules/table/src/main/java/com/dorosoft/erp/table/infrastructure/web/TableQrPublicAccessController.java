package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableQrAccessService;
import com.dorosoft.erp.table.application.dto.QrTableAccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qr/table-access")
class TableQrPublicAccessController {

    private final TableQrAccessService service;
    private final TableQrHostValidator hostValidator;
    private final TableQrPublicRateLimiter rateLimiter;

    TableQrPublicAccessController(
            TableQrAccessService service,
            TableQrHostValidator hostValidator,
            TableQrPublicRateLimiter rateLimiter) {
        this.service = service;
        this.hostValidator = hostValidator;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    ResponseEntity<QrTableAccessResponse> verify(
            @RequestBody(required = false) QrTableAccessRequest request,
            HttpServletRequest servletRequest) {
        hostValidator.verify(servletRequest);
        rateLimiter.verify(servletRequest);
        String token = request == null ? null : request.token();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.verify(token));
    }

    record QrTableAccessRequest(String token) {}
}
