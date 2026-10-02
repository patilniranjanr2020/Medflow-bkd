package com.medflow.modules.patients.domain.service;

import com.medflow.modules.patients.api.request.CreatePatientRequest;
import com.medflow.modules.patients.api.request.UpdatePatientRequest;
import com.medflow.modules.settings.api.PatientProfileResolver;
import com.medflow.shared.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;

/** Validates patient input against the hospital's effective registration profile. */
@Service
public class PatientRegistrationProfileValidator {

  private final PatientProfileResolver profileResolver;

  public PatientRegistrationProfileValidator(PatientProfileResolver profileResolver) {
    this.profileResolver = profileResolver;
  }

  public void validateCreate(Long hospitalId, CreatePatientRequest request) {
    validate(hospitalId, request.firstName(), request.dateOfBirth(), request.gender(), request.phone(),
        request.email(), request.address(), request.bloodGroup(), request.emergencyContactName(),
        request.emergencyContactPhone(), request.city(), request.state(), request.postalCode(),
        request.preferredLanguage(), request.emergencyContactRelationship(), request.insuranceProvider(),
        request.memberId(), request.governmentIdType(), request.governmentIdNumber(), request.allergies(),
        request.consentStatus(), request.referringPhysician(), request.guardianName(),
        request.guardianRelationship(), request.guardianMobile());
  }

  public void validateUpdate(Long hospitalId, UpdatePatientRequest request) {
    validate(hospitalId, request.firstName(), request.dateOfBirth(), request.gender(), request.phone(),
        request.email(), request.address(), request.bloodGroup(), request.emergencyContactName(),
        request.emergencyContactPhone(), request.city(), request.state(), request.postalCode(),
        request.preferredLanguage(), request.emergencyContactRelationship(), request.insuranceProvider(),
        request.memberId(), request.governmentIdType(), request.governmentIdNumber(), request.allergies(),
        request.consentStatus(), request.referringPhysician(), request.guardianName(),
        request.guardianRelationship(), request.guardianMobile());
  }

  public void validateUpdate(Long hospitalId, com.medflow.modules.patients.domain.entity.Patient existingPatient, UpdatePatientRequest request) {
    String firstName = request.firstName() != null ? request.firstName() : existingPatient.getFirstName();
    java.time.LocalDate dateOfBirth = request.dateOfBirth() != null ? request.dateOfBirth() : existingPatient.getDateOfBirth();
    com.medflow.shared.domain.Gender gender = request.gender() != null ? request.gender() : existingPatient.getGender();
    String phone = request.phone() != null ? request.phone() : existingPatient.getPhone();
    String email = request.email() != null ? request.email() : existingPatient.getEmail();
    String address = request.address() != null ? request.address() : existingPatient.getAddress();
    String bloodGroup = request.bloodGroup() != null ? request.bloodGroup() : existingPatient.getBloodGroup();
    String emergencyContactName = request.emergencyContactName() != null ? request.emergencyContactName() : existingPatient.getEmergencyContactName();
    String emergencyContactPhone = request.emergencyContactPhone() != null ? request.emergencyContactPhone() : existingPatient.getEmergencyContactPhone();
    String city = request.city() != null ? request.city() : existingPatient.getCity();
    String state = request.state() != null ? request.state() : existingPatient.getState();
    String postalCode = request.postalCode() != null ? request.postalCode() : existingPatient.getPostalCode();
    String preferredLanguage = request.preferredLanguage() != null ? request.preferredLanguage() : existingPatient.getPreferredLanguage();
    String emergencyContactRelationship = request.emergencyContactRelationship() != null ? request.emergencyContactRelationship() : existingPatient.getEmergencyContactRelationship();
    String insuranceProvider = request.insuranceProvider() != null ? request.insuranceProvider() : existingPatient.getInsuranceProvider();
    String memberId = request.memberId() != null ? request.memberId() : existingPatient.getMemberId();
    String governmentIdType = request.governmentIdType() != null ? request.governmentIdType() : existingPatient.getGovernmentIdType();
    String governmentIdNumber = request.governmentIdNumber() != null ? request.governmentIdNumber() : existingPatient.getGovernmentIdNumber();
    String allergies = request.allergies() != null ? request.allergies() : existingPatient.getAllergies();
    String consentStatus = request.consentStatus() != null ? request.consentStatus() : existingPatient.getConsentStatus();
    String referringPhysician = request.referringPhysician() != null ? request.referringPhysician() : existingPatient.getReferringPhysician();
    String guardianName = request.guardianName() != null ? request.guardianName() : existingPatient.getGuardianName();
    String guardianRelationship = request.guardianRelationship() != null ? request.guardianRelationship() : existingPatient.getGuardianRelationship();
    String guardianMobile = request.guardianMobile() != null ? request.guardianMobile() : existingPatient.getGuardianMobile();

    validate(hospitalId, firstName, dateOfBirth, gender, phone, email, address, bloodGroup,
        emergencyContactName, emergencyContactPhone, city, state, postalCode, preferredLanguage,
        emergencyContactRelationship, insuranceProvider, memberId, governmentIdType,
        governmentIdNumber, allergies, consentStatus, referringPhysician, guardianName,
        guardianRelationship, guardianMobile);
  }

  private void validate(Long hospitalId, String firstName, java.time.LocalDate dateOfBirth,
      com.medflow.shared.domain.Gender gender, String phone, String email, String address,
      String bloodGroup, String emergencyContactName, String emergencyContactPhone, String city,
      String state, String postalCode, String preferredLanguage, String emergencyContactRelationship,
      String insuranceProvider, String memberId, String governmentIdType, String governmentIdNumber,
      String allergies, String consentStatus, String referringPhysician, String guardianName,
      String guardianRelationship, String guardianMobile) {
    var profile = profileResolver.resolveProfile(hospitalId);
    require(profile, "FULL_NAME", firstName, "Full Name");
    require(profile, "DATE_OF_BIRTH", dateOfBirth, "Date of Birth");
    require(profile, "GENDER", gender, "Gender");
    require(profile, "MOBILE", phone, "Mobile");
    require(profile, "EMAIL", email, "Email");
    rejectHidden(profile, "ADDRESS", address, "Address");
    rejectHidden(profile, "BLOOD_GROUP", bloodGroup, "Blood Group");
    rejectHidden(profile, "EMERGENCY_CONTACT_NAME", emergencyContactName, "Emergency Contact Name");
    rejectHidden(profile, "EMERGENCY_CONTACT_MOBILE", emergencyContactPhone, "Emergency Contact Mobile");
    rejectHidden(profile, "CITY", city, "City");
    rejectHidden(profile, "STATE", state, "State");
    rejectHidden(profile, "POSTAL_CODE", postalCode, "PIN code");
    rejectHidden(profile, "PREFERRED_LANGUAGE", preferredLanguage, "Preferred Language");
    rejectHidden(profile, "EMERGENCY_CONTACT_RELATIONSHIP", emergencyContactRelationship, "Emergency Contact Relationship");
    rejectHidden(profile, "INSURANCE_PROVIDER", insuranceProvider, "Insurance Provider");
    rejectHidden(profile, "MEMBER_ID", memberId, "Member ID");
    rejectHidden(profile, "GOVERNMENT_ID_TYPE", governmentIdType, "Government ID Type");
    rejectHidden(profile, "GOVERNMENT_ID_NUMBER", governmentIdNumber, "Government ID Number");
    rejectHidden(profile, "ALLERGIES", allergies, "Allergies");
    rejectHidden(profile, "CONSENT_STATUS", consentStatus, "Consent Status");
    rejectHidden(profile, "REFERRING_PHYSICIAN", referringPhysician, "Referring Physician");
    rejectHidden(profile, "GUARDIAN_NAME", guardianName, "Guardian Name");
    rejectHidden(profile, "GUARDIAN_RELATIONSHIP", guardianRelationship, "Guardian Relationship");
    rejectHidden(profile, "GUARDIAN_MOBILE", guardianMobile, "Guardian Mobile");
  }

  private static void require(PatientProfileResolver.EffectiveProfile profile, String key,
      Object value, String label) {
    if (profile.isFieldRequired(key) && isBlank(value)) {
      throw new BusinessRuleViolationException(label + " is required by the hospital registration profile");
    }
    rejectHidden(profile, key, value, label);
  }

  private static void rejectHidden(PatientProfileResolver.EffectiveProfile profile, String key,
      Object value, String label) {
    if (!isBlank(value) && !profile.isFieldVisible(key)) {
      throw new BusinessRuleViolationException(label + " is not enabled for patient registration");
    }
  }

  private static boolean isBlank(Object value) {
    return value == null || (value instanceof String string && string.isBlank());
  }
}