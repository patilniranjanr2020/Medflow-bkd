package com.medflow.modules.patients.controller;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.patients.api.PatientService;
import com.medflow.modules.patients.api.request.AddMedicalHistoryRequest;
import com.medflow.modules.patients.api.request.AddPatientReportRequest;
import com.medflow.modules.patients.api.request.CreatePatientRequest;
import com.medflow.modules.patients.api.request.LinkPatientAccountRequest;
import com.medflow.modules.patients.api.request.UpdatePatientRequest;
import com.medflow.modules.patients.api.response.MedicalHistoryResponse;
import com.medflow.modules.patients.api.response.PatientAccountResponse;
import com.medflow.modules.patients.api.response.PatientReportResponse;
import com.medflow.modules.patients.api.response.PatientResponse;
import com.medflow.shared.api.ApiResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.domain.AccountStatus;
import com.medflow.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
class PatientController {

  private final PatientService service;
  private final TenantContext tenantContext;
  private final AuditService auditService;

  PatientController(PatientService service, TenantContext tenantContext, AuditService auditService) {
    this.service = service;
    this.tenantContext = tenantContext;
    this.auditService = auditService;
  }

  @PostMapping
  @Operation(summary = "Register patient", description = "Creates a patient record with intake details.")
  ResponseEntity<ApiResponse<PatientResponse>> create(
      @Valid @RequestBody CreatePatientRequest request) {
    var created = service.create(tenantContext.hospitalId(), request);
    auditService.record("PATIENT_CREATED", "patient", created.id(),
        "{\"patientCode\":\"%s\"}".formatted(created.patientCode()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Patient created successfully", created));
  }

  @GetMapping
  @Operation(summary = "List patients",
      description = "Searches by name, code, phone or email; filterable by status.")
  ApiResponse<PageResponse<PatientResponse>> search(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) AccountStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success("Patients retrieved successfully",
        service.search(tenantContext.hospitalId(), query, status, page, size));
  }

  @GetMapping("/{patientId}")
  @Operation(summary = "Get patient", description = "Returns a patient by identifier.")
  ApiResponse<PatientResponse> find(@PathVariable Long patientId) {
    var patient = service.findById(tenantContext.hospitalId(), patientId);
    auditService.record("PATIENT_VIEWED", "patient", patientId, null);
    return ApiResponse.success("Patient retrieved successfully", patient);
  }

  @PutMapping("/{patientId}")
  @Operation(summary = "Update patient",
      description = "Replaces the patient's profile, including archive status.")
  ApiResponse<PatientResponse> update(@PathVariable Long patientId,
      @Valid @RequestBody UpdatePatientRequest request) {
    var updated = service.update(tenantContext.hospitalId(), patientId, request);
    auditService.record("PATIENT_UPDATED", "patient", patientId, null);
    return ApiResponse.success("Patient updated successfully", updated);
  }

  @GetMapping("/{patientId}/medical-history")
  @Operation(summary = "List medical history", description = "Recorded conditions, newest first.")
  ApiResponse<List<MedicalHistoryResponse>> medicalHistory(@PathVariable Long patientId) {
    return ApiResponse.success("Medical history retrieved successfully",
        service.medicalHistory(tenantContext.hospitalId(), patientId));
  }

  @PostMapping("/{patientId}/medical-history")
  @Operation(summary = "Add medical history", description = "Records a condition for the patient.")
  ResponseEntity<ApiResponse<MedicalHistoryResponse>> addMedicalHistory(
      @PathVariable Long patientId, @Valid @RequestBody AddMedicalHistoryRequest request) {
    var created = service.addMedicalHistory(tenantContext.hospitalId(), patientId, request);
    auditService.record("PATIENT_HISTORY_ADDED", "patient", patientId,
        "{\"condition\":\"%s\"}".formatted(created.conditionName()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Medical history recorded successfully", created));
  }

  @GetMapping("/{patientId}/accounts")
  @Operation(summary = "List linked accounts",
      description = "Portal accounts allowed to act for this patient and how they are related.")
  ApiResponse<List<PatientAccountResponse>> linkedAccounts(@PathVariable Long patientId) {
    return ApiResponse.success("Linked accounts retrieved successfully",
        service.linkedAccounts(tenantContext.hospitalId(), patientId));
  }

  @PostMapping("/{patientId}/accounts")
  @Operation(summary = "Link account",
      description = "Grants a portal account (self, parent, guardian, …) access to this patient.")
  ResponseEntity<ApiResponse<PatientAccountResponse>> linkAccount(@PathVariable Long patientId,
      @Valid @RequestBody LinkPatientAccountRequest request) {
    var created = service.linkAccount(tenantContext.hospitalId(), patientId, request);
    auditService.record("PATIENT_ACCOUNT_LINKED", "patient", patientId,
        "{\"userId\":%d,\"relation\":\"%s\"}".formatted(request.userId(), request.relation()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Account linked successfully", created));
  }

  @GetMapping("/{patientId}/reports")
  @Operation(summary = "List reports", description = "Documents filed against the patient.")
  ApiResponse<List<PatientReportResponse>> reports(@PathVariable Long patientId) {
    return ApiResponse.success("Reports retrieved successfully",
        service.reports(tenantContext.hospitalId(), patientId));
  }

  @PostMapping("/{patientId}/reports")
  @Operation(summary = "Attach report",
      description = "Registers a stored document (scan, lab report, discharge note) for the patient.")
  ResponseEntity<ApiResponse<PatientReportResponse>> addReport(@PathVariable Long patientId,
      @Valid @RequestBody AddPatientReportRequest request) {
    var created = service.addReport(tenantContext.hospitalId(), patientId,
        tenantContext.userId(), request);
    auditService.record("PATIENT_REPORT_ADDED", "patient", patientId,
        "{\"reportType\":\"%s\"}".formatted(created.reportType()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Report attached successfully", created));
  }
}
