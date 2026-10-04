package com.medflow.modules.pharmacy.controller;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.pharmacy.api.MedicationService;
import com.medflow.modules.pharmacy.api.request.AdjustStockRequest;
import com.medflow.modules.pharmacy.api.request.CreateMedicationRequest;
import com.medflow.modules.pharmacy.api.request.UpdateMedicationRequest;
import com.medflow.modules.pharmacy.api.response.MedicationResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/pharmacy/medications")
class MedicationController {

  private final MedicationService service;
  private final TenantContext tenantContext;
  private final AuditService auditService;

  MedicationController(MedicationService service, TenantContext tenantContext,
      AuditService auditService) {
    this.service = service;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
  @Operation(summary = "Add medication",
      description = "Registers a medication in the pharmacy catalogue.")
  ResponseEntity<ApiResponse<MedicationResponse>> create(
      @Valid @RequestBody CreateMedicationRequest request) {
    var created = service.create(tenantContext.hospitalId(), request);
    auditService.record("MEDICATION_CREATED", "medication", created.id(),
        "{\"name\":\"%s\"}".formatted(created.name()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        "Medication created successfully", created));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE')")
  @Operation(summary = "List medications",
      description = "Searches the catalogue with query, category, and stock status filters.")
  ApiResponse<PageResponse<MedicationResponse>> search(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String stockStatus,
      @RequestParam(defaultValue = "false") boolean lowStockOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success("Medications retrieved successfully",
        service.search(tenantContext.hospitalId(), query, category, stockStatus, lowStockOnly, page, size));
  }

  @GetMapping("/categories")
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE')")
  @Operation(summary = "List distinct medication categories",
      description = "Returns all unique categories among active medications in the hospital catalogue.")
  ApiResponse<List<String>> categories() {
    return ApiResponse.success("Categories retrieved successfully",
        service.categories(tenantContext.hospitalId()));
  }

  @GetMapping("/{medicationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE')")
  @Operation(summary = "Get medication", description = "Returns a medication by identifier.")
  ApiResponse<MedicationResponse> find(@PathVariable Long medicationId) {
    return ApiResponse.success("Medication retrieved successfully",
        service.findById(tenantContext.hospitalId(), medicationId));
  }

  @PutMapping("/{medicationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
  @Operation(summary = "Update medication",
      description = "Updates catalogue details and the reorder level.")
  ApiResponse<MedicationResponse> update(@PathVariable Long medicationId,
      @Valid @RequestBody UpdateMedicationRequest request) {
    var updated = service.update(tenantContext.hospitalId(), medicationId, request);
    auditService.record("MEDICATION_UPDATED", "medication", medicationId,
        "{\"name\":\"%s\"}".formatted(updated.name()));
    return ApiResponse.success("Medication updated successfully", updated);
  }

  @DeleteMapping("/{medicationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
  @Operation(summary = "Delete medication",
      description = "Soft-deletes a medication from the catalogue.")
  ApiResponse<Void> delete(@PathVariable Long medicationId) {
    service.delete(tenantContext.hospitalId(), medicationId);
    auditService.record("MEDICATION_DELETED", "medication", medicationId, null);
    return ApiResponse.success("Medication deleted successfully", null);
  }

  @PatchMapping("/{medicationId}/stock")
  @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'NURSE')")
  @Operation(summary = "Adjust stock",
      description = "Restocks (+) or dispenses (−); crossing the reorder level raises an alert.")
  ApiResponse<MedicationResponse> adjustStock(@PathVariable Long medicationId,
      @Valid @RequestBody AdjustStockRequest request) {
    var updated = service.adjustStock(tenantContext.hospitalId(), medicationId, request);
    auditService.record("MEDICATION_STOCK_ADJUSTED", "medication", medicationId,
        "{\"delta\":%d}".formatted(request.delta()));
    return ApiResponse.success("Medication stock adjusted successfully", updated);
  }
}
