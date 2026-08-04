package com.dorosoft.erp.store.presentation;

import com.dorosoft.erp.identity.application.authentication.AuthenticatedActorResolver;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import com.dorosoft.erp.store.application.dto.StoreFeatureSettingsUpdateResponse;
import com.dorosoft.erp.store.application.dto.StoreProfileUpdateResponse;
import com.dorosoft.erp.store.application.dto.StoreScheduleUpdateResponse;
import com.dorosoft.erp.store.application.dto.StoreSettingsResponse;
import com.dorosoft.erp.store.application.dto.StoreSettingsWebMapper;
import com.dorosoft.erp.store.application.feature.UpdateFeatureSettingsService;
import com.dorosoft.erp.store.application.profile.UpdateStoreProfileService;
import com.dorosoft.erp.store.application.schedule.UpdateOperatingScheduleService;
import com.dorosoft.erp.store.application.settings.GetStoreSettingsService;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import com.dorosoft.erp.store.presentation.common.ApiResponse;
import com.dorosoft.erp.store.presentation.dto.UpdateFeatureSettingsRequest;
import com.dorosoft.erp.store.presentation.dto.UpdateScheduleRequest;
import com.dorosoft.erp.store.presentation.dto.UpdateStoreProfileRequest;
import com.dorosoft.erp.store.presentation.exception.IfMatchMissingOrInvalidException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-settings")
public class StoreSettingsController {

    private final GetStoreSettingsService getStoreSettingsService;
    private final UpdateStoreProfileService updateStoreProfileService;
    private final UpdateOperatingScheduleService updateOperatingScheduleService;
    private final UpdateFeatureSettingsService updateFeatureSettingsService;
    private final AuthenticatedActorResolver actorResolver;

    public StoreSettingsController(
            GetStoreSettingsService getStoreSettingsService,
            UpdateStoreProfileService updateStoreProfileService,
            UpdateOperatingScheduleService updateOperatingScheduleService,
            UpdateFeatureSettingsService updateFeatureSettingsService,
            AuthenticatedActorResolver actorResolver) {
        this.getStoreSettingsService = getStoreSettingsService;
        this.updateStoreProfileService = updateStoreProfileService;
        this.updateOperatingScheduleService = updateOperatingScheduleService;
        this.updateFeatureSettingsService = updateFeatureSettingsService;
        this.actorResolver = actorResolver;
    }

    @PreAuthorize("hasAuthority('store.settings.update')")
    @PutMapping("/features")
    public ApiResponse<StoreFeatureSettingsUpdateResponse> updateFeatures(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateFeatureSettingsRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        long requestedVersion = parseIfMatch(ifMatch);
        String requestId = requestId(servletRequest);
        StoreSettings saved = updateFeatureSettingsService.update(
                request.toCommand(requestedVersion), actorResolver.resolve(authentication), requestId);
        return new ApiResponse<>(
                new StoreFeatureSettingsUpdateResponse(
                        StoreSettingsWebMapper.toFeatureResponse(saved.features()), saved.version()),
                requestId);
    }

    @PreAuthorize("hasAuthority('store.settings.update')")
    @PutMapping("/schedule")
    public ApiResponse<StoreScheduleUpdateResponse> updateSchedule(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateScheduleRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        long requestedVersion = parseIfMatch(ifMatch);
        String requestId = requestId(servletRequest);
        StoreSettings saved = updateOperatingScheduleService.update(
                request.toCommand(requestedVersion), actorResolver.resolve(authentication), requestId);
        return new ApiResponse<>(
                new StoreScheduleUpdateResponse(
                        StoreSettingsWebMapper.toScheduleResponse(saved.schedule()), saved.version()),
                requestId);
    }

    @PreAuthorize("hasAuthority('store.settings.read')")
    @GetMapping("")
    public ApiResponse<StoreSettingsResponse> get(HttpServletRequest servletRequest) {
        return new ApiResponse<>(getStoreSettingsService.get(), requestId(servletRequest));
    }

    @PreAuthorize("hasAuthority('store.settings.update')")
    @PutMapping("/profile")
    public ApiResponse<StoreProfileUpdateResponse> updateProfile(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateStoreProfileRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        long requestedVersion = parseIfMatch(ifMatch);
        String requestId = requestId(servletRequest);
        StoreSettings saved = updateStoreProfileService.update(
                request.toCommand(requestedVersion), actorResolver.resolve(authentication), requestId);
        return new ApiResponse<>(
                new StoreProfileUpdateResponse(
                        StoreSettingsWebMapper.toProfileResponse(saved.profile()), saved.version()),
                requestId);
    }

    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null) {
            throw new IfMatchMissingOrInvalidException();
        }
        String value = ifMatch.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IfMatchMissingOrInvalidException();
        }
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_REQUEST_ID);
    }
}
