package com.medflow.modules.users.controller;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.users.api.UserAccountService;
import com.medflow.modules.users.api.request.ChangeRoleRequest;
import com.medflow.modules.users.api.request.ChangeStatusRequest;
import com.medflow.modules.users.api.request.CreateUserAccountRequest;
import com.medflow.modules.users.api.request.UpdateUserProfileRequest;
import com.medflow.modules.users.api.response.UserAccountResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

  private final UserAccountService service;
  private final TenantContext tenantContext;
  private final AuditService auditService;

  UserController(UserAccountService service, TenantContext tenantContext, AuditService auditService) {
    this.service = service;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
  }

  @GetMapping
  @Operation(summary = "List users", description = "Searches this hospital's staff directory.")
  ApiResponse<PageResponse<UserAccountResponse>> search(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String roleCode,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success("Users retrieved successfully",
        service.search(tenantContext.hospitalId(), query, roleCode, page, size));
  }

  @GetMapping("/{userId}")
  @Operation(summary = "Get user", description = "Returns one account from this hospital.")
  ApiResponse<UserAccountResponse> find(@PathVariable Long userId) {
    return ApiResponse.success("User retrieved successfully",
        service.getById(tenantContext.hospitalId(), userId));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Create user", description = "Admin-only: invites a staff account with an explicit role.")
  ResponseEntity<ApiResponse<UserAccountResponse>> create(
      @Valid @RequestBody CreateUserAccountRequest request) {
    var created = service.create(tenantContext.hospitalId(), request);
    auditService.record("USER_CREATED", "user", created.id(),
        "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(created.email(), created.roleCode()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("User created successfully", created));
  }

  @PutMapping("/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update profile", description = "Updates a staff member's personal details.")
  ApiResponse<UserAccountResponse> updateProfile(@PathVariable Long userId,
      @Valid @RequestBody UpdateUserProfileRequest request) {
    var updated = service.updateProfile(tenantContext.hospitalId(), userId, request);
    auditService.record("USER_UPDATED", "user", userId, null);
    return ApiResponse.success("User updated successfully", updated);
  }

  @PatchMapping("/{userId}/role")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Change role", description = "Admin-only: reassigns the user's role and portal.")
  ApiResponse<UserAccountResponse> changeRole(@PathVariable Long userId,
      @Valid @RequestBody ChangeRoleRequest request) {
    var updated = service.changeRole(tenantContext.hospitalId(), userId, request.roleCode());
    auditService.record("USER_ROLE_CHANGED", "user", userId,
        "{\"role\":\"%s\"}".formatted(request.roleCode()));
    return ApiResponse.success("User role updated successfully", updated);
  }

  @PatchMapping("/{userId}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Change status",
      description = "Admin-only: activates, deactivates or suspends an account.")
  ApiResponse<UserAccountResponse> changeStatus(@PathVariable Long userId,
      @Valid @RequestBody ChangeStatusRequest request) {
    var updated = service.changeStatus(tenantContext.hospitalId(), userId, request.status());
    auditService.record("USER_STATUS_CHANGED", "user", userId,
        "{\"status\":\"%s\"}".formatted(request.status()));
    return ApiResponse.success("User status updated successfully", updated);
  }
}
