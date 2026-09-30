package com.medflow.modules.settings.domain.service;

import com.medflow.modules.settings.domain.entity.PatientRegistrationProfileField.FieldState;
import com.medflow.shared.exception.BusinessRuleViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Enforces baseline rules for patient registration profile configurations.
 *
 * <p>Rules:
 * <ul>
 *   <li>FULL_NAME (firstName + lastName) cannot be Hidden</li>
 *   <li>DATE_OF_BIRTH cannot be Hidden</li>
 *   <li>GENDER cannot be Hidden</li>
 *   <li>At least one of MOBILE or EMAIL must be visible (Required or Optional)</li>
 * </ul>
 */
@Service
public class ProfileValidationPolicy {

  private static final String FULL_NAME = "FULL_NAME";
  private static final String DATE_OF_BIRTH = "DATE_OF_BIRTH";
  private static final String GENDER = "GENDER";
  private static final String MOBILE = "MOBILE";
  private static final String EMAIL = "EMAIL";

  /**
   * Validates field configurations against baseline rules.
   *
   * @param fieldConfigurations map of field key to desired state
   * @throws BusinessRuleViolationException if any rule is violated
   */
  public void validate(Map<String, FieldState> fieldConfigurations) {
    // Rule 1: FULL_NAME cannot be Hidden
    if (fieldConfigurations.getOrDefault(FULL_NAME, FieldState.REQUIRED) == FieldState.HIDDEN) {
      throw new BusinessRuleViolationException("Full Name is a required field and cannot be hidden");
    }

    // Rule 2: DATE_OF_BIRTH cannot be Hidden
    if (fieldConfigurations.getOrDefault(DATE_OF_BIRTH, FieldState.REQUIRED) == FieldState.HIDDEN) {
      throw new BusinessRuleViolationException("Date of Birth is a required field and cannot be hidden");
    }

    // Rule 3: GENDER cannot be Hidden
    if (fieldConfigurations.getOrDefault(GENDER, FieldState.REQUIRED) == FieldState.HIDDEN) {
      throw new BusinessRuleViolationException("Gender is a required field and cannot be hidden");
    }

    // Rule 4: At least one of MOBILE or EMAIL must be visible
    FieldState mobileState = fieldConfigurations.getOrDefault(MOBILE, FieldState.OPTIONAL);
    FieldState emailState = fieldConfigurations.getOrDefault(EMAIL, FieldState.OPTIONAL);

    boolean mobileSeen = mobileState != FieldState.HIDDEN;
    boolean emailSeen = emailState != FieldState.HIDDEN;

    if (!mobileSeen && !emailSeen) {
      throw new BusinessRuleViolationException(
          "At least one contact method (Mobile or Email) must be visible for patient registration");
    }
  }

  /**
   * Gets a list of validation error messages for the given configurations.
   * Returns empty list if all rules pass.
   */
  public List<String> validateAndGetErrors(Map<String, FieldState> fieldConfigurations) {
    return List.of(); // Placeholder; would gather all violations
  }
}
