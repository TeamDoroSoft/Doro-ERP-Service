package com.dorosoft.erp.catalog.presentation.query;

import com.dorosoft.erp.catalog.application.api.PublishedMenu;
import com.dorosoft.erp.catalog.application.api.PublishedMenuReader;
import com.dorosoft.erp.catalog.presentation.common.CatalogApiEnvelope;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 메뉴 조회(GET /menu). 인증이 필요 없다(SecurityFilterChain permitAll). */
@RestController
@RequestMapping("/api/v1/menu")
public class PublicMenuController {

    private final PublishedMenuReader publishedMenuReader;

    public PublicMenuController(PublishedMenuReader publishedMenuReader) {
        this.publishedMenuReader = publishedMenuReader;
    }

    @GetMapping
    public ResponseEntity<CatalogApiEnvelope<PublishedMenu>> getMenu(HttpServletRequest request) {
        PublishedMenu menu = publishedMenuReader.getPublishedMenu();
        String requestId = CatalogRequestId.from(request);
        String etag = "\"catalog-" + menu.catalogRevision() + "\"";
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(CatalogApiEnvelope.of(menu, requestId));
    }
}
