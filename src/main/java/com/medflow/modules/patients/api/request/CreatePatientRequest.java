package com.medflow.modules.patients.api.request;

import com.medflow.shared.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreatePatientRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    Gender gender,
    @Past LocalDate dateOfBirth,
    @Pattern(regexp = "(A|B|AB|O)[+-]", message = "must be a valid blood group such as O+ or AB-")
    String bloodGroup,
    @Size(max = 20) String phone,
    @Email @Size(max = 120) String email,
    @Size(max = 1000) String address,
    @Size(max = 100) String emergencyContactName,
    @Size(max = 20) String emergencyContactPhone,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Pattern(regexp = "[1-9][0-9]{5}", message = "must be a valid 6-digit Indian PIN code")
    String postalCode,
    @Size(max = 50) String preferredLanguage,
    @Size(max = 50) String emergencyContactRelationship,
    @Size(max = 150) String insuranceProvider,
    @Size(max = 100) String memberId,
    @Size(max = 50) String governmentIdType,
    @Size(max = 100) String governmentIdNumber,
    @Size(max = 2000) String allergies,
    @Size(max = 50) String consentStatus,
    @Size(max = 150) String referringPhysician,
    @Size(max = 100) String guardianName,
    @Size(max = 50) String guardianRelationship,
    @Size(max = 20) String guardianMobile) {
}
