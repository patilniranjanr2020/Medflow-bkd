package com.medflow.modules.settings.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** One field configuration within a patient registration profile. */
@Entity
@Table(name = "patient_registration_profile_fields")
public class PatientRegistrationProfileField {

  public enum FieldState {
    REQUIRED, OPTIONAL, HIDDEN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "profile_id", nullable = false)
  private Long profileId;

  @Column(name = "field_key", nullable = false, length = 50)
  private String fieldKey;

  @Column(name = "field_label", nullable = false, length = 100)
  private String fieldLabel;

  @Column(name = "field_group", nullable = false, length = 50)
  private String fieldGroup;

  @Column(name = "field_state", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private FieldState fieldState;

  @Column(name = "field_order", nullable = false)
  private int fieldOrder = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PatientRegistrationProfileField() {
  }

  public PatientRegistrationProfileField(Long profileId, String fieldKey, String fieldLabel,
      String fieldGroup, FieldState fieldState, int fieldOrder) {
    this.profileId = profileId;
    this.fieldKey = fieldKey;
    this.fieldLabel = fieldLabel;
    this.fieldGroup = fieldGroup;
    this.fieldState = fieldState;
    this.fieldOrder = fieldOrder;
    this.createdAt = Instant.now();
  }

  public void updateState(FieldState newState) {
    this.fieldState = newState;
  }

  public Long getId() { return id; }
  public Long getProfileId() { return profileId; }
  public String getFieldKey() { return fieldKey; }
  public String getFieldLabel() { return fieldLabel; }
  public String getFieldGroup() { return fieldGroup; }
  public FieldState getFieldState() { return fieldState; }
  public int getFieldOrder() { return fieldOrder; }
  public Instant getCreatedAt() { return createdAt; }
}
