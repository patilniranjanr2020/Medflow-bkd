package com.medflow.modules.laboratory.controller;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.laboratory.api.LabOrderService;
import com.medflow.modules.laboratory.api.LabOrderStatus;
import com.medflow.modules.laboratory.api.LabPriority;
import com.medflow.modules.laboratory.api.request.CompleteLabOrderRequest;
import com.medflow.modules.laboratory.api.request.CreateLabOrderRequest;
import com.medflow.modules.laboratory.api.request.UpdateLabOrderRequest;
import com.medflow.modules.laboratory.api.response.LabOrderResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/lab-orders")
class LabOrderController {

  private final LabOrderService service;
  private final TenantContext tenantContext;
  private final AuditService auditService;

  LabOrderController(LabOrderService service, TenantContext tenantContext,
      AuditService auditService) {
    this.service = service;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Create lab order", description = "Routes a test order into the specimen queue.")
  ResponseEntity<ApiResponse<LabOrderResponse>> create(
      @Valid @RequestBody CreateLabOrderRequest request) {
    var created = service.create(tenantContext.hospitalId(), request);
    auditService.record("LAB_ORDER_CREATED", "lab_order", created.id(),
        "{\"testName\":\"%s\"}".formatted(created.testName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        "Lab order created successfully", created));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN')")
  @Operation(summary = "List lab orders",
      description = "Filters the queue by query, status, priority and patient.")
  ApiResponse<PageResponse<LabOrderResponse>> search(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) LabOrderStatus status,
      @RequestParam(required = false) LabPriority priority,
      @RequestParam(required = false) Long patientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success("Lab orders retrieved successfully",
        service.search(tenantContext.hospitalId(), query, status, priority, patientId, page, size));
  }

  @GetMapping("/{labOrderId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN')")
  @Operation(summary = "Get lab order", description = "Returns a lab order by identifier.")
  ApiResponse<LabOrderResponse> find(@PathVariable Long labOrderId) {
    var order = service.findById(tenantContext.hospitalId(), labOrderId);
    auditService.record("LAB_ORDER_VIEWED", "lab_order", labOrderId, null);
    return ApiResponse.success("Lab order retrieved successfully", order);
  }

  @PutMapping("/{labOrderId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Update lab order",
      description = "Updates order details and/or in-progress result.")
  ApiResponse<LabOrderResponse> update(@PathVariable Long labOrderId,
      @Valid @RequestBody UpdateLabOrderRequest request) {
    var updated = service.update(tenantContext.hospitalId(), labOrderId, request);
    auditService.record("LAB_ORDER_UPDATED", "lab_order", labOrderId,
        "{\"testName\":\"%s\"}".formatted(updated.testName()));
    return ApiResponse.success("Lab order updated successfully", updated);
  }

  @DeleteMapping("/{labOrderId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Delete lab order", description = "Soft-deletes a lab order from the queue.")
  ApiResponse<Void> delete(@PathVariable Long labOrderId) {
    service.delete(tenantContext.hospitalId(), labOrderId);
    auditService.record("LAB_ORDER_DELETED", "lab_order", labOrderId, null);
    return ApiResponse.success("Lab order deleted successfully", null);
  }

  @PatchMapping("/{labOrderId}/start")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Start processing", description = "ORDERED → IN_PROGRESS.")
  ApiResponse<LabOrderResponse> start(@PathVariable Long labOrderId) {
    var updated = service.startProcessing(tenantContext.hospitalId(), labOrderId);
    auditService.record("LAB_ORDER_STARTED", "lab_order", labOrderId, null);
    return ApiResponse.success("Lab order moved to processing", updated);
  }

  @PatchMapping("/{labOrderId}/complete")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Record results",
      description = "Signs off results and notifies the workspace.")
  ApiResponse<LabOrderResponse> complete(@PathVariable Long labOrderId,
      @Valid @RequestBody CompleteLabOrderRequest request) {
    var updated = service.complete(tenantContext.hospitalId(), labOrderId, request);
    auditService.record("LAB_ORDER_COMPLETED", "lab_order", labOrderId, "{\"result\":\"recorded\"}");
    return ApiResponse.success("Lab order completed successfully", updated);
  }

  @PatchMapping("/{labOrderId}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECHNICIAN')")
  @Operation(summary = "Cancel lab order",
      description = "Cancels an order that has not been completed.")
  ApiResponse<LabOrderResponse> cancel(@PathVariable Long labOrderId) {
    var updated = service.cancel(tenantContext.hospitalId(), labOrderId);
    auditService.record("LAB_ORDER_CANCELLED", "lab_order", labOrderId, null);
    return ApiResponse.success("Lab order cancelled successfully", updated);
  }
}
