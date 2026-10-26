package com.medflow.modules.patients.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medflow.modules.patients.api.request.CreatePatientRequest;
import com.medflow.modules.settings.api.PatientProfileResolver;
import com.medflow.modules.settings.api.PatientProfileResolver.EffectiveProfile;
import com.medflow.modules.settings.api.PatientProfileResolver.FieldConfig;
import com.medflow.shared.domain.Gender;
import com.medflow.shared.exception.BusinessRuleViolationException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientRegistrationProfileValidatorTest {

  private PatientProfileResolver profileResolver;
  private PatientRegistrationProfileValidator validator;

  @BeforeEach
  void setUp() {
    profileResolver = mock(PatientProfileResolver.class);
    validator = new PatientRegistrationProfileValidator(profileResolver);
  }

  @Test
  @DisplayName("Should pass when all required fields are provided and no hidden fields are populated")
  void shouldPassValidRegistration() {
    var effectiveProfile = new EffectiveProfile(1L, "Basic", List.of(
        new FieldConfig("FULL_NAME", "Full Name", "Identity", true, 1),
        new FieldConfig("DATE_OF_BIRTH", "Date of Birth", "Identity", true, 2),
        new FieldConfig("GENDER", "Gender", "Identity", true, 3),
        new FieldConfig("MOBILE", "Mobile", "Contact", true, 4),
        new FieldConfig("EMAIL", "Email", "Contact", false, 5)
    ));
    when(profileResolver.resolveProfile(100L)).thenReturn(effectiveProfile);

    var request = new CreatePatientRequest(
        "Aarav", "Sharma", Gender.MALE, LocalDate.of(1990, 1, 1),
        null, "9876543210", "aarav@test.local", null,
        null, null, null, null, null, null, null,
        null, null, null, null, null, null, null,
        null, null, null
    );

    assertThatCode(() -> validator.validateCreate(100L, request))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should reject when a required field configured in profile is missing")
  void shouldRejectMissingRequiredField() {
    var effectiveProfile = new EffectiveProfile(1L, "Custom", List.of(
        new FieldConfig("FULL_NAME", "Full Name", "Identity", true, 1),
        new FieldConfig("DATE_OF_BIRTH", "Date of Birth", "Identity", true, 2),
        new FieldConfig("GENDER", "Gender", "Identity", true, 3),
        new FieldConfig("MOBILE", "Mobile", "Contact", true, 4),
        new FieldConfig("BLOOD_GROUP", "Blood Group", "Clinical", true, 5)
    ));
    when(profileResolver.resolveProfile(100L)).thenReturn(effectiveProfile);

    var request = new CreatePatientRequest(
        "Aarav", "Sharma", Gender.MALE, LocalDate.of(1990, 1, 1),
        null, "9876543210", null, null,
        null, null, null, null, null, null, null,
        null, null, null, null, null, null, null,
        null, null, null
    );

    assertThatThrownBy(() -> validator.validateCreate(100L, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Blood Group is required by the hospital registration profile");
  }

  @Test
  @DisplayName("Should reject when a hidden field is provided in the request")
  void shouldRejectHiddenFieldProvided() {
    // Effective profile does not include INSURANCE_PROVIDER (it is HIDDEN)
    var effectiveProfile = new EffectiveProfile(1L, "Basic", List.of(
        new FieldConfig("FULL_NAME", "Full Name", "Identity", true, 1),
        new FieldConfig("DATE_OF_BIRTH", "Date of Birth", "Identity", true, 2),
        new FieldConfig("GENDER", "Gender", "Identity", true, 3),
        new FieldConfig("MOBILE", "Mobile", "Contact", true, 4)
    ));
    when(profileResolver.resolveProfile(100L)).thenReturn(effectiveProfile);

    var request = new CreatePatientRequest(
        "Aarav", "Sharma", Gender.MALE, LocalDate.of(1990, 1, 1),
        null, "9876543210", null, null,
        null, null, null, null, null, null, null,
        "Secret Insurance Inc", null, null, null, null, null, null,
        null, null, null
    );

    assertThatThrownBy(() -> validator.validateCreate(100L, request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Insurance Provider is not enabled for patient registration");
  }
}
