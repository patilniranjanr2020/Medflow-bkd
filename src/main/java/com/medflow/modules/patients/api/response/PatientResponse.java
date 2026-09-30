package com.medflow.modules.patients.api.response;

import com.medflow.shared.domain.AccountStatus;
import com.medflow.shared.domain.Gender;
import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
    Long id,
    Long hospitalId,
    Long userId,
    String patientCode,
    String firstName,
    String lastName,
    String fullName,
    Gender gender,
    LocalDate dateOfBirth,
    Integer age,
    String bloodGroup,
    String phone,
    String email,
    String address,
    String emergencyContactName,
    String emergencyContactPhone,
    String city,
    String state,
    String postalCode,
    String preferredLanguage,
    String emergencyContactRelationship,
    String insuranceProvider,
    String memberId,
    String governmentIdType,
    String governmentIdNumber,
    String allergies,
    String consentStatus,
    String referringPhysician,
    String guardianName,
    String guardianRelationship,
    String guardianMobile,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt) {
}
