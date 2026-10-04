package com.medflow.modules.laboratory.domain.entity;

import com.medflow.modules.laboratory.api.LabOrderStatus;
import com.medflow.modules.laboratory.api.LabPriority;
import com.medflow.shared.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lab_orders")
public class LabOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "doctor_id", nullable = false)
  private Long doctorId;

  @Column(name = "test_name", nullable = false, length = 150)
  private String testName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LabPriority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LabOrderStatus status;

  @Column(name = "result_summary", length = 2000)
  private String resultSummary;

  @Column(name = "ordered_at", nullable = false, updatable = false)
  private Instant orderedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  protected LabOrder() {
  }

  public LabOrder(Long hospitalId, Long patientId, Long doctorId, String testName,
      LabPriority priority) {
    this.hospitalId = hospitalId;
    this.patientId = patientId;
    this.doctorId = doctorId;
    this.testName = testName;
    this.priority = priority;
    this.status = LabOrderStatus.ORDERED;
    this.deleted = false;
    this.orderedAt = Instant.now();
    this.updatedAt = this.orderedAt;
  }

  public void startProcessing() {
    requireStatus(LabOrderStatus.ORDERED, "Only newly ordered tests can start processing");
    this.status = LabOrderStatus.IN_PROGRESS;
    touch();
  }

  public void complete(String resultSummary) {
    if (status != LabOrderStatus.ORDERED && status != LabOrderStatus.IN_PROGRESS) {
      throw new BusinessRuleViolationException(
          "A " + status.name().toLowerCase() + " lab order cannot be completed");
    }
    this.status = LabOrderStatus.COMPLETED;
    this.resultSummary = resultSummary;
    this.completedAt = Instant.now();
    touch();
  }

  public void cancel() {
    if (status == LabOrderStatus.COMPLETED || status == LabOrderStatus.CANCELLED) {
      throw new BusinessRuleViolationException(
          "A " + status.name().toLowerCase() + " lab order cannot be cancelled");
    }
    this.status = LabOrderStatus.CANCELLED;
    touch();
  }

  public void updateDetails(String testName, LabPriority priority, Long doctorId) {
    if (status == LabOrderStatus.CANCELLED) {
      throw new BusinessRuleViolationException("A cancelled lab order cannot be modified");
    }
    if (testName != null && !testName.isBlank()) {
      this.testName = testName.trim();
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (doctorId != null) {
      this.doctorId = doctorId;
    }
    touch();
  }

  public void updateResult(String resultSummary) {
    if (status == LabOrderStatus.CANCELLED) {
      throw new BusinessRuleViolationException("A cancelled lab order cannot have its result updated");
    }
    if (status == LabOrderStatus.ORDERED) {
      this.status = LabOrderStatus.IN_PROGRESS;
    }
    this.resultSummary = resultSummary;
    touch();
  }

  public void markDeleted() {
    this.deleted = true;
    touch();
  }

  private void requireStatus(LabOrderStatus expected, String message) {
    if (status != expected) {
      throw new BusinessRuleViolationException(message);
    }
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getHospitalId() { return hospitalId; }
  public Long getPatientId() { return patientId; }
  public Long getDoctorId() { return doctorId; }
  public String getTestName() { return testName; }
  public LabPriority getPriority() { return priority; }
  public LabOrderStatus getStatus() { return status; }
  public String getResultSummary() { return resultSummary; }
  public Instant getOrderedAt() { return orderedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public boolean isDeleted() { return deleted; }
}
