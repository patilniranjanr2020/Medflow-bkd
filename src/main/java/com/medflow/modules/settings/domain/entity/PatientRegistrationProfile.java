package com.medflow.modules.settings.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Hospital-scoped configuration for patient registration fields. */
@Entity
@Table(name = "patient_registration_profiles")
public class PatientRegistrationProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "profile_name", nullable = false, length = 100)
  private String profileName;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "version", nullable = false)
  private int version = 1;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PatientRegistrationProfile() {
  }

  public PatientRegistrationProfile(Long hospitalId, String profileName, Long updatedBy) {
    this.hospitalId = hospitalId;
    this.profileName = profileName;
    this.updatedBy = updatedBy;
    this.active = true;
    this.version = 1;
    this.updatedAt = Instant.now();
    this.createdAt = Instant.now();
  }

  public void incrementVersion() {
    this.version += 1;
    this.updatedAt = Instant.now();
  }

  public void updateProfileName(String profileName, Long updatedBy) {
    this.profileName = profileName;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getHospitalId() { return hospitalId; }
  public String getProfileName() { return profileName; }
  public boolean isActive() { return active; }
  public int getVersion() { return version; }
  public Long getUpdatedBy() { return updatedBy; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getCreatedAt() { return createdAt; }
}
