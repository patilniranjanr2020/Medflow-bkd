package com.medflow.modules.settings.api;

import com.medflow.modules.settings.api.request.UpdateProfileRequest;
import com.medflow.modules.settings.api.response.ProfileResponse;

/** Public API for patient registration profile management. */
public interface PatientRegistrationProfileService {

  /**
   * Get the active patient registration profile for a hospital.
   * Returns a default profile if none exists yet.
   */
  ProfileResponse getProfile(Long hospitalId);

  /**
   * Update the patient registration profile fields.
   * Validates against baseline rules (full name, DOB, gender cannot be hidden; at least one
   * contact method visible).
   *
  * @throws com.medflow.shared.exception.BusinessRuleViolationException if validation fails
   * @throws com.medflow.shared.exception.OptimisticLockException if version mismatch
   */
  ProfileResponse updateProfile(Long hospitalId, Long userId, UpdateProfileRequest request);

  /** Apply the Basic template (minimal fields required). */
  ProfileResponse applyBasicTemplate(Long hospitalId, Long userId);

  /** Apply the Comprehensive template (all fields optional except baseline). */
  ProfileResponse applyComprehensiveTemplate(Long hospitalId, Long userId);
}
