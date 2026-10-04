package com.medflow.modules.prescriptions.controller;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.prescriptions.api.PrescriptionService;
import com.medflow.modules.prescriptions.api.PrescriptionStatus;
import com.medflow.modules.prescriptions.api.request.CreatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.request.UpdatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.response.PrescriptionResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/prescriptions")
class PrescriptionController {

  private final PrescriptionService service;
  private final TenantContext tenantContext;
  private final AuditService auditService;

  PrescriptionController(PrescriptionService service, TenantContext tenantContext,
      AuditService auditService) {
    this.service = service;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
  }

  @PostMapping
  @Operation(summary = "Issue prescription",
      description = "Writes a diagnosis with one or more medication lines and signs it.")
  ResponseEntity<ApiResponse<PrescriptionResponse>> create(
      @Valid @RequestBody CreatePrescriptionRequest request) {
    var created = service.create(tenantContext.hospitalId(), request);
    auditService.record("PRESCRIPTION_CREATED", "prescription", created.id(),
        "{\"patientId\":%d,\"doctorId\":%d}".formatted(created.patientId(), created.doctorId()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        "Prescription created successfully", created));
  }

  @GetMapping
  @Operation(summary = "List prescriptions",
      description = "Filters by patient (history), doctor and status.")
  ApiResponse<PageResponse<PrescriptionResponse>> search(
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) PrescriptionStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success("Prescriptions retrieved successfully",
        service.search(tenantContext.hospitalId(), patientId, doctorId, status, page, size));
  }

  @GetMapping("/{prescriptionId}")
  @Operation(summary = "Get prescription", description = "Returns a prescription with its medicines.")
  ApiResponse<PrescriptionResponse> find(@PathVariable Long prescriptionId) {
    return ApiResponse.success("Prescription retrieved successfully",
        service.findById(tenantContext.hospitalId(), prescriptionId));
  }

  @PutMapping("/{prescriptionId}")
  @Operation(summary = "Update prescription",
      description = "Updates diagnosis and medication lines on an active unsigned draft prescription.")
  ApiResponse<PrescriptionResponse> update(@PathVariable Long prescriptionId,
      @Valid @RequestBody UpdatePrescriptionRequest request) {
    var updated = service.update(tenantContext.hospitalId(), prescriptionId, request);
    auditService.record("PRESCRIPTION_UPDATED", "prescription", prescriptionId,
        "{\"patientId\":%d,\"doctorId\":%d}".formatted(updated.patientId(), updated.doctorId()));
    return ApiResponse.success("Prescription updated successfully", updated);
  }

  @DeleteMapping("/{prescriptionId}")
  @Operation(summary = "Delete prescription",
      description = "Deletes an active unsigned draft prescription.")
  ApiResponse<Void> delete(@PathVariable Long prescriptionId) {
    service.delete(tenantContext.hospitalId(), prescriptionId);
    auditService.record("PRESCRIPTION_DELETED", "prescription", prescriptionId, null);
    return ApiResponse.success("Prescription deleted successfully", null);
  }

  @PatchMapping("/{prescriptionId}/complete")
  @Operation(summary = "Complete prescription", description = "Marks the course as finished.")
  ApiResponse<PrescriptionResponse> complete(@PathVariable Long prescriptionId) {
    var completed = service.complete(tenantContext.hospitalId(), prescriptionId);
    auditService.record("PRESCRIPTION_COMPLETED", "prescription", prescriptionId, null);
    return ApiResponse.success("Prescription completed successfully", completed);
  }

  @PatchMapping("/{prescriptionId}/cancel")
  @Operation(summary = "Cancel prescription", description = "Withdraws an active prescription.")
  ApiResponse<PrescriptionResponse> cancel(@PathVariable Long prescriptionId) {
    var cancelled = service.cancel(tenantContext.hospitalId(), prescriptionId);
    auditService.record("PRESCRIPTION_CANCELLED", "prescription", prescriptionId, null);
    return ApiResponse.success("Prescription cancelled successfully", cancelled);
  }
}
