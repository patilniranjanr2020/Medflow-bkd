package com.medflow.modules.patients.domain.entity;

import com.medflow.modules.patients.api.request.UpdatePatientRequest;
import com.medflow.shared.domain.AccountStatus;
import com.medflow.shared.domain.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "patients")
public class Patient {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Set when the patient also has a portal login of their own. */
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "patient_code", nullable = false, length = 30)
  private String patientCode;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(length = 10)
  private Gender gender;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "blood_group", length = 5)
  private String bloodGroup;

  @Column(length = 20)
  private String phone;

  @Column(length = 120)
  private String email;

  @Column(length = 1000)
  private String address;

  @Column(name = "emergency_contact_name", length = 100)
  private String emergencyContactName;

  @Column(name = "emergency_contact_phone", length = 20)
  private String emergencyContactPhone;

  @Column(length = 100) private String city;
  @Column(length = 100) private String state;
  @Column(name = "postal_code", length = 6) private String postalCode;
  @Column(name = "preferred_language", length = 50) private String preferredLanguage;
  @Column(name = "emergency_contact_relationship", length = 50) private String emergencyContactRelationship;
  @Column(name = "insurance_provider", length = 150) private String insuranceProvider;
  @Column(name = "member_id", length = 100) private String memberId;
  @Column(name = "government_id_type", length = 50) private String governmentIdType;
  @Column(name = "government_id_number", length = 100) private String governmentIdNumber;
  @Column(length = 2000) private String allergies;
  @Column(name = "consent_status", length = 50) private String consentStatus;
  @Column(name = "referring_physician", length = 150) private String referringPhysician;
  @Column(name = "guardian_name", length = 100) private String guardianName;
  @Column(name = "guardian_relationship", length = 50) private String guardianRelationship;
  @Column(name = "guardian_mobile", length = 20) private String guardianMobile;

  @Column(nullable = false, length = 30)
  private AccountStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  protected Patient() {
  }

  public Patient(Long hospitalId, String patientCode, String firstName, String lastName,
      Gender gender, LocalDate dateOfBirth, String bloodGroup, String phone, String email,
      String address, String emergencyContactName, String emergencyContactPhone,
      String city, String state, String postalCode, String preferredLanguage,
      String emergencyContactRelationship, String insuranceProvider, String memberId,
      String governmentIdType, String governmentIdNumber, String allergies, String consentStatus,
      String referringPhysician, String guardianName, String guardianRelationship,
      String guardianMobile) {
    this.hospitalId = hospitalId;
    this.patientCode = patientCode;
    this.firstName = firstName;
    this.lastName = lastName;
    this.gender = gender;
    this.dateOfBirth = dateOfBirth;
    this.bloodGroup = bloodGroup;
    this.phone = phone;
    this.email = email;
    this.address = address;
    this.emergencyContactName = emergencyContactName;
    this.emergencyContactPhone = emergencyContactPhone;
    this.city = city;
    this.state = state;
    this.postalCode = postalCode;
    this.preferredLanguage = preferredLanguage;
    this.emergencyContactRelationship = emergencyContactRelationship;
    this.insuranceProvider = insuranceProvider;
    this.memberId = memberId;
    this.governmentIdType = governmentIdType;
    this.governmentIdNumber = governmentIdNumber;
    this.allergies = allergies;
    this.consentStatus = consentStatus;
    this.referringPhysician = referringPhysician;
    this.guardianName = guardianName;
    this.guardianRelationship = guardianRelationship;
    this.guardianMobile = guardianMobile;
    this.status = AccountStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
    this.deleted = false;
  }

  public void update(UpdatePatientRequest request) {
    if (request.firstName() != null) {
      if (request.firstName().isBlank()) {
        throw new com.medflow.shared.exception.BusinessRuleViolationException("First name cannot be blank");
      }
      this.firstName = request.firstName().trim();
    }
    if (request.lastName() != null) {
      this.lastName = request.lastName().trim().isEmpty() ? null : request.lastName().trim();
    }
    if (request.gender() != null) {
      this.gender = request.gender();
    }
    if (request.dateOfBirth() != null) {
      this.dateOfBirth = request.dateOfBirth();
    }
    if (request.bloodGroup() != null) {
      this.bloodGroup = request.bloodGroup().trim().isEmpty() ? null : request.bloodGroup().trim();
    }
    if (request.phone() != null) {
      this.phone = request.phone().trim().isEmpty() ? null : request.phone().trim();
    }
    if (request.email() != null) {
      this.email = request.email().trim().isEmpty() ? null : request.email().trim();
    }
    if (request.address() != null) {
      this.address = request.address().trim().isEmpty() ? null : request.address().trim();
    }
    if (request.emergencyContactName() != null) {
      this.emergencyContactName = request.emergencyContactName().trim().isEmpty() ? null : request.emergencyContactName().trim();
    }
    if (request.emergencyContactPhone() != null) {
      this.emergencyContactPhone = request.emergencyContactPhone().trim().isEmpty() ? null : request.emergencyContactPhone().trim();
    }
    if (request.city() != null) {
      this.city = request.city().trim().isEmpty() ? null : request.city().trim();
    }
    if (request.state() != null) {
      this.state = request.state().trim().isEmpty() ? null : request.state().trim();
    }
    if (request.postalCode() != null) {
      this.postalCode = request.postalCode().trim().isEmpty() ? null : request.postalCode().trim();
    }
    if (request.preferredLanguage() != null) {
      this.preferredLanguage = request.preferredLanguage().trim().isEmpty() ? null : request.preferredLanguage().trim();
    }
    if (request.emergencyContactRelationship() != null) {
      this.emergencyContactRelationship = request.emergencyContactRelationship().trim().isEmpty() ? null : request.emergencyContactRelationship().trim();
    }
    if (request.insuranceProvider() != null) {
      this.insuranceProvider = request.insuranceProvider().trim().isEmpty() ? null : request.insuranceProvider().trim();
    }
    if (request.memberId() != null) {
      this.memberId = request.memberId().trim().isEmpty() ? null : request.memberId().trim();
    }
    if (request.governmentIdType() != null) {
      this.governmentIdType = request.governmentIdType().trim().isEmpty() ? null : request.governmentIdType().trim();
    }
    if (request.governmentIdNumber() != null) {
      this.governmentIdNumber = request.governmentIdNumber().trim().isEmpty() ? null : request.governmentIdNumber().trim();
    }
    if (request.allergies() != null) {
      this.allergies = request.allergies().trim().isEmpty() ? null : request.allergies().trim();
    }
    if (request.consentStatus() != null) {
      this.consentStatus = request.consentStatus().trim().isEmpty() ? null : request.consentStatus().trim();
    }
    if (request.referringPhysician() != null) {
      this.referringPhysician = request.referringPhysician().trim().isEmpty() ? null : request.referringPhysician().trim();
    }
    if (request.guardianName() != null) {
      this.guardianName = request.guardianName().trim().isEmpty() ? null : request.guardianName().trim();
    }
    if (request.guardianRelationship() != null) {
      this.guardianRelationship = request.guardianRelationship().trim().isEmpty() ? null : request.guardianRelationship().trim();
    }
    if (request.guardianMobile() != null) {
      this.guardianMobile = request.guardianMobile().trim().isEmpty() ? null : request.guardianMobile().trim();
    }
    if (request.status() != null) {
      this.status = request.status();
    }
    this.updatedAt = Instant.now();
  }

  public void linkAccount(Long userId) {
    this.userId = userId;
    this.updatedAt = Instant.now();
  }

  public String getFullName() {
    return (lastName == null || lastName.isBlank()) ? firstName : firstName + " " + lastName;
  }

  public Integer getAge() {
    return dateOfBirth == null ? null : Period.between(dateOfBirth, LocalDate.now()).getYears();
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public Long getHospitalId() { return hospitalId; }
  public String getPatientCode() { return patientCode; }
  public String getFirstName() { return firstName; }
  public String getLastName() { return lastName; }
  public Gender getGender() { return gender; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public String getBloodGroup() { return bloodGroup; }
  public String getPhone() { return phone; }
  public String getEmail() { return email; }
  public String getAddress() { return address; }
  public String getEmergencyContactName() { return emergencyContactName; }
  public String getEmergencyContactPhone() { return emergencyContactPhone; }
  public String getCity() { return city; }
  public String getState() { return state; }
  public String getPostalCode() { return postalCode; }
  public String getPreferredLanguage() { return preferredLanguage; }
  public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
  public String getInsuranceProvider() { return insuranceProvider; }
  public String getMemberId() { return memberId; }
  public String getGovernmentIdType() { return governmentIdType; }
  public String getGovernmentIdNumber() { return governmentIdNumber; }
  public String getAllergies() { return allergies; }
  public String getConsentStatus() { return consentStatus; }
  public String getReferringPhysician() { return referringPhysician; }
  public String getGuardianName() { return guardianName; }
  public String getGuardianRelationship() { return guardianRelationship; }
  public String getGuardianMobile() { return guardianMobile; }
  public AccountStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public boolean isDeleted() { return deleted; }
}
