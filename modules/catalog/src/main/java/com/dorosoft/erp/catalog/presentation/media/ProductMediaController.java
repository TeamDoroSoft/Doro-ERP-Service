package com.dorosoft.erp.catalog.presentation.media;

import com.dorosoft.erp.catalog.application.media.CompleteMediaUploadService;
import com.dorosoft.erp.catalog.application.media.RequestMediaUploadCommand;
import com.dorosoft.erp.catalog.application.media.RequestMediaUploadService;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.presentation.common.CatalogApiEnvelope;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import com.dorosoft.erp.catalog.presentation.dto.MediaCompleteResponse;
import com.dorosoft.erp.catalog.presentation.dto.MediaUploadTicketResponse;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.platform.web.ApiErrorCode;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/media-uploads")
public class ProductMediaController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RequestMediaUploadService requestMediaUploadService;
    private final CompleteMediaUploadService completeMediaUploadService;

    public ProductMediaController(
            RequestMediaUploadService requestMediaUploadService, CompleteMediaUploadService completeMediaUploadService) {
        this.requestMediaUploadService = requestMediaUploadService;
        this.completeMediaUploadService = completeMediaUploadService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<MediaUploadTicketResponse>> requestUpload(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RequestMediaUploadRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        UUID requestedBy = requestedBy(authentication);
        var ticket =
                requestMediaUploadService.requestUpload(
                        new RequestMediaUploadCommand(
                                body.contentType(), body.byteSize(), body.checksumSha256(), requestedBy, idempotencyKey));
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.status(201)
                .body(CatalogApiEnvelope.of(MediaUploadTicketResponse.from(ticket), requestId));
    }

    @PostMapping("/{mediaId}/complete")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<MediaCompleteResponse>> complete(
            @PathVariable UUID mediaId, HttpServletRequest request) {
        ProductMedia media = completeMediaUploadService.complete(mediaId);
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok(CatalogApiEnvelope.of(MediaCompleteResponse.from(media), requestId));
    }

    private static UUID requestedBy(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            throw new ProblemAwareException(ApiErrorCode.VALIDATION_FAILED, "인증 정보가 없습니다");
        }
        return principal.accountId();
    }

    public record RequestMediaUploadRequest(
            @NotBlank String contentType, @PositiveOrZero long byteSize, @NotBlank String checksumSha256) {}
}
