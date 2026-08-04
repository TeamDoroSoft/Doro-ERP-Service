package com.dorosoft.erp.store.presentation;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import com.dorosoft.erp.store.application.availability.GetPublicStoreAvailabilityService;
import com.dorosoft.erp.store.application.dto.PublicStoreAvailabilityResponse;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.presentation.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/availability")
public class PublicStoreAvailabilityController {

    private final GetPublicStoreAvailabilityService service;

    public PublicStoreAvailabilityController(GetPublicStoreAvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PublicStoreAvailabilityResponse> get(
            @RequestParam FeatureCode featureCode, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
        return new ApiResponse<>(service.get(featureCode), requestId);
    }
}
