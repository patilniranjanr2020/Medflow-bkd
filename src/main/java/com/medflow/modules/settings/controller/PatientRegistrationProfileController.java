package com.medflow.modules.settings.controller;

import com.medflow.modules.settings.api.PatientRegistrationProfileService;
import com.medflow.modules.settings.api.request.UpdateProfileRequest;
import com.medflow.modules.settings.api.response.ProfileResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.exception.OptimisticLockException;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/settings/patient-registration-profile")
@Validated
@Tag(name = "Patient Registration Profile", description = "Hospital-admin APIs to configure patient registration fields")
class PatientRegistrationProfileController {

  private final PatientRegistrationProfileService service;
  private final TenantContext tenantContext;

  PatientRegistrationProfileController(
      PatientRegistrationProfileService service,
      TenantContext tenantContext) {
    this.service = service;
    this.tenantContext = tenantContext;
  }

  @GetMapping
  @Operation(summary = "Get active profile",
      description = "Retrieves the active patient registration profile for this hospital")
  ApiResponse<ProfileResponse> getProfile() {
    return ApiResponse.success("Profile retrieved successfully",
        service.getProfile(tenantContext.hospitalId()));
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update profile",
      description = "Admin-only: Updates patient registration fields. Returns 409 if version mismatch.")
  ApiResponse<ProfileResponse> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request) {
    try {
      return ApiResponse.success("Profile updated successfully",
          service.updateProfile(tenantContext.hospitalId(), tenantContext.userId(), request));
    } catch (OptimisticLockException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
  }

  @PostMapping("/templates/basic")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Apply basic template",
      description = "Admin-only: Apply the Basic template (minimal required fields)")
  ApiResponse<ProfileResponse> applyBasicTemplate() {
    return ApiResponse.success("Basic template applied successfully",
        service.applyBasicTemplate(tenantContext.hospitalId(), tenantContext.userId()));
  }

  @PostMapping("/templates/comprehensive")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Apply comprehensive template",
      description = "Admin-only: Apply the Comprehensive template (all fields optional except required baseline)")
  ApiResponse<ProfileResponse> applyComprehensiveTemplate() {
    return ApiResponse.success("Comprehensive template applied successfully",
        service.applyComprehensiveTemplate(tenantContext.hospitalId(), tenantContext.userId()));
  }
}
