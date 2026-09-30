package com.medflow.modules.settings.application;

import com.medflow.modules.settings.api.PatientRegistrationProfileChangedEvent;
import com.medflow.modules.settings.api.PatientRegistrationProfileService;
import com.medflow.modules.settings.api.PatientProfileResolver;
import com.medflow.modules.settings.api.request.UpdateProfileRequest;
import com.medflow.modules.settings.api.response.PatientRegistrationFieldResponse;
import com.medflow.modules.settings.api.response.ProfileResponse;
import com.medflow.modules.settings.domain.entity.PatientRegistrationProfile;
import com.medflow.modules.settings.domain.entity.PatientRegistrationProfileField;
import com.medflow.modules.settings.domain.entity.PatientRegistrationProfileField.FieldState;
import com.medflow.modules.settings.domain.repository.PatientRegistrationProfileFieldRepository;
import com.medflow.modules.settings.domain.repository.PatientRegistrationProfileRepository;
import com.medflow.modules.settings.domain.service.ProfileValidationPolicy;
import com.medflow.shared.exception.OptimisticLockException;
import com.medflow.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PatientRegistrationProfileServiceImpl implements PatientRegistrationProfileService {

  private final PatientRegistrationProfileRepository profileRepository;
  private final PatientRegistrationProfileFieldRepository fieldRepository;
  private final ProfileValidationPolicy validationPolicy;
  private final ApplicationEventPublisher eventPublisher;
  private final ProfileCacheManager cacheManager;
  private final PatientProfileResolver resolutionService;

  PatientRegistrationProfileServiceImpl(
      PatientRegistrationProfileRepository profileRepository,
      PatientRegistrationProfileFieldRepository fieldRepository,
      ProfileValidationPolicy validationPolicy,
      ApplicationEventPublisher eventPublisher,
      ProfileCacheManager cacheManager,
      PatientProfileResolver resolutionService) {
    this.profileRepository = profileRepository;
    this.fieldRepository = fieldRepository;
    this.validationPolicy = validationPolicy;
    this.eventPublisher = eventPublisher;
    this.cacheManager = cacheManager;
    this.resolutionService = resolutionService;
  }

  @Override
  @Transactional
  public ProfileResponse getProfile(Long hospitalId) {
    // Check cache first
    var cached = cacheManager.get(hospitalId);
    if (cached.isPresent()) {
      return cached.get();
    }

    var profile = profileRepository.findByHospitalIdAndActive(hospitalId, true)
        .orElseGet(() -> initializeDefaultProfile(hospitalId));
    var fields = fieldRepository.findByProfileIdOrderByFieldOrder(profile.getId());
    var response = toResponse(profile, fields);
    cacheManager.put(hospitalId, response);
    return response;
  }

  @Override
  @Transactional
  public ProfileResponse updateProfile(Long hospitalId, Long userId, UpdateProfileRequest request) {
    var profile = profileRepository.findByHospitalIdAndActive(hospitalId, true)
        .orElseThrow(() -> new ResourceNotFoundException("Patient registration profile not found"));

    // Optimistic concurrency check
    if (profile.getVersion() != request.version()) {
      throw new OptimisticLockException(
          "Profile version mismatch. Expected " + profile.getVersion() + " but got "
              + request.version() + ". Please refresh and try again.");
    }

    // Convert request to map and validate
    Map<String, FieldState> fieldConfigs = request.fields().stream()
        .collect(Collectors.toMap(
            UpdateProfileRequest.FieldStateRequest::fieldKey,
            f -> FieldState.valueOf(f.state())));

    validationPolicy.validate(fieldConfigs);

    // Capture previous state for audit
    Map<String, Map<String, String>> changes = captureChanges(profile.getId(), fieldConfigs);

    // Delete old fields and create new ones
    fieldRepository.deleteByProfileId(profile.getId());
    fieldRepository.flush();
    int order = 0;
    for (var fieldReq : request.fields()) {
      var field = new PatientRegistrationProfileField(
          profile.getId(),
          fieldReq.fieldKey(),
          fieldReq.fieldKey(),  // Use key as label for now; frontend will map
          getFieldGroup(fieldReq.fieldKey()),
          FieldState.valueOf(fieldReq.state()),
          order++);
      fieldRepository.save(field);
    }

    // Update profile version and timestamp
    profile.incrementVersion();
    profile.updateProfileName("Custom", userId);
    var updated = profileRepository.save(profile);

    // Emit event for audit
    eventPublisher.publishEvent(
        new PatientRegistrationProfileChangedEvent(hospitalId, updated.getId(), changes, userId,
            Instant.now()));

    // Invalidate cache
    cacheManager.invalidate(hospitalId);
    resolutionService.invalidateCache(hospitalId);

    var fields = fieldRepository.findByProfileIdOrderByFieldOrder(updated.getId());
    return toResponse(updated, fields);
  }

  @Override
  @Transactional
  public ProfileResponse applyBasicTemplate(Long hospitalId, Long userId) {
    var fieldConfigs = getBasicTemplateConfig();
    var request = new UpdateProfileRequest(
        currentVersion(hospitalId),
        fieldConfigs.entrySet().stream()
            .map(e -> new UpdateProfileRequest.FieldStateRequest(e.getKey(), e.getValue().name()))
            .collect(Collectors.toList()));

    return updateProfile(hospitalId, userId, request);
  }

  @Override
  @Transactional
  public ProfileResponse applyComprehensiveTemplate(Long hospitalId, Long userId) {
    var fieldConfigs = getComprehensiveTemplateConfig();
    var request = new UpdateProfileRequest(
      currentVersion(hospitalId),
        fieldConfigs.entrySet().stream()
            .map(e -> new UpdateProfileRequest.FieldStateRequest(e.getKey(), e.getValue().name()))
            .collect(Collectors.toList()));

    return updateProfile(hospitalId, userId, request);
  }

  private int currentVersion(Long hospitalId) {
    return profileRepository.findByHospitalIdAndActive(hospitalId, true)
        .map(PatientRegistrationProfile::getVersion)
      .orElseGet(() -> initializeDefaultProfile(hospitalId).getVersion());
  }

  /**
   * Initializes the default profile for a hospital.
   * Returns a profile with Basic template configuration.
   */
  private PatientRegistrationProfile initializeDefaultProfile(Long hospitalId) {
    var profile = new PatientRegistrationProfile(hospitalId, "Default", null);
    var saved = profileRepository.save(profile);

    // Create default fields (Basic template)
    var fieldConfigs = getBasicTemplateConfig();
    int order = 0;
    for (var entry : fieldConfigs.entrySet()) {
      var field = new PatientRegistrationProfileField(
          saved.getId(), entry.getKey(), entry.getKey(), getFieldGroup(entry.getKey()),
          entry.getValue(), order++);
      fieldRepository.save(field);
    }

    return saved;
  }

  /**
   * Basic template: only essential fields required
   */
  private static Map<String, FieldState> getBasicTemplateConfig() {
    Map<String, FieldState> config = new HashMap<>();
    // Identity
    config.put("FULL_NAME", FieldState.REQUIRED);
    config.put("DATE_OF_BIRTH", FieldState.REQUIRED);
    config.put("GENDER", FieldState.REQUIRED);
    config.put("PATIENT_CODE", FieldState.OPTIONAL);
    // Contact
    config.put("MOBILE", FieldState.REQUIRED);
    config.put("EMAIL", FieldState.OPTIONAL);
    config.put("ADDRESS", FieldState.HIDDEN);
    config.put("CITY", FieldState.HIDDEN);
    config.put("STATE", FieldState.HIDDEN);
    config.put("POSTAL_CODE", FieldState.HIDDEN);
    config.put("PREFERRED_LANGUAGE", FieldState.HIDDEN);
    // Emergency
    config.put("EMERGENCY_CONTACT_NAME", FieldState.HIDDEN);
    config.put("EMERGENCY_CONTACT_RELATIONSHIP", FieldState.HIDDEN);
    config.put("EMERGENCY_CONTACT_MOBILE", FieldState.HIDDEN);
    // Insurance
    config.put("INSURANCE_PROVIDER", FieldState.HIDDEN);
    config.put("MEMBER_ID", FieldState.HIDDEN);
    config.put("GOVERNMENT_ID_TYPE", FieldState.HIDDEN);
    config.put("GOVERNMENT_ID_NUMBER", FieldState.HIDDEN);
    // Clinical
    config.put("BLOOD_GROUP", FieldState.HIDDEN);
    config.put("ALLERGIES", FieldState.HIDDEN);
    config.put("CONSENT_STATUS", FieldState.HIDDEN);
    config.put("REFERRING_PHYSICIAN", FieldState.HIDDEN);
    // Guardian
    config.put("GUARDIAN_NAME", FieldState.HIDDEN);
    config.put("GUARDIAN_RELATIONSHIP", FieldState.HIDDEN);
    config.put("GUARDIAN_MOBILE", FieldState.HIDDEN);
    return config;
  }

  /**
   * Comprehensive template: all fields optional except baseline required
   */
  private static Map<String, FieldState> getComprehensiveTemplateConfig() {
    Map<String, FieldState> config = new HashMap<>();
    // Identity
    config.put("FULL_NAME", FieldState.REQUIRED);
    config.put("DATE_OF_BIRTH", FieldState.REQUIRED);
    config.put("GENDER", FieldState.REQUIRED);
    config.put("PATIENT_CODE", FieldState.OPTIONAL);
    // Contact
    config.put("MOBILE", FieldState.REQUIRED);
    config.put("EMAIL", FieldState.OPTIONAL);
    config.put("ADDRESS", FieldState.OPTIONAL);
    config.put("CITY", FieldState.OPTIONAL);
    config.put("STATE", FieldState.OPTIONAL);
    config.put("POSTAL_CODE", FieldState.OPTIONAL);
    config.put("PREFERRED_LANGUAGE", FieldState.OPTIONAL);
    // Emergency
    config.put("EMERGENCY_CONTACT_NAME", FieldState.OPTIONAL);
    config.put("EMERGENCY_CONTACT_RELATIONSHIP", FieldState.OPTIONAL);
    config.put("EMERGENCY_CONTACT_MOBILE", FieldState.OPTIONAL);
    // Insurance
    config.put("INSURANCE_PROVIDER", FieldState.OPTIONAL);
    config.put("MEMBER_ID", FieldState.OPTIONAL);
    config.put("GOVERNMENT_ID_TYPE", FieldState.OPTIONAL);
    config.put("GOVERNMENT_ID_NUMBER", FieldState.OPTIONAL);
    // Clinical
    config.put("BLOOD_GROUP", FieldState.OPTIONAL);
    config.put("ALLERGIES", FieldState.OPTIONAL);
    config.put("CONSENT_STATUS", FieldState.OPTIONAL);
    config.put("REFERRING_PHYSICIAN", FieldState.OPTIONAL);
    // Guardian
    config.put("GUARDIAN_NAME", FieldState.OPTIONAL);
    config.put("GUARDIAN_RELATIONSHIP", FieldState.OPTIONAL);
    config.put("GUARDIAN_MOBILE", FieldState.OPTIONAL);
    return config;
  }

  /**
   * Maps field key to its display group
   */
  private static String getFieldGroup(String fieldKey) {
    return switch (fieldKey) {
      case "FULL_NAME", "DATE_OF_BIRTH", "GENDER", "PATIENT_CODE" -> "Identity";
      case "MOBILE", "EMAIL", "ADDRESS", "CITY", "STATE", "POSTAL_CODE", "PREFERRED_LANGUAGE" ->
          "Contact";
      case "EMERGENCY_CONTACT_NAME", "EMERGENCY_CONTACT_RELATIONSHIP", "EMERGENCY_CONTACT_MOBILE" ->
          "Emergency";
      case "INSURANCE_PROVIDER", "MEMBER_ID", "GOVERNMENT_ID_TYPE", "GOVERNMENT_ID_NUMBER" ->
          "Insurance & ID";
      case "BLOOD_GROUP", "ALLERGIES", "CONSENT_STATUS", "REFERRING_PHYSICIAN" -> "Clinical";
      case "GUARDIAN_NAME", "GUARDIAN_RELATIONSHIP", "GUARDIAN_MOBILE" -> "Guardian";
      default -> "Other";
    };
  }

  /**
   * Captures changes between previous and new field states for audit
   */
  private Map<String, Map<String, String>> captureChanges(Long profileId,
      Map<String, FieldState> newStates) {
    Map<String, Map<String, String>> changes = new HashMap<>();

    var oldFields = fieldRepository.findByProfileIdOrderByFieldOrder(profileId);
    Map<String, FieldState> oldStates =
        oldFields.stream()
            .collect(Collectors.toMap(
                PatientRegistrationProfileField::getFieldKey,
                PatientRegistrationProfileField::getFieldState));

    for (var entry : newStates.entrySet()) {
      var fieldKey = entry.getKey();
      var newState = entry.getValue();
      var oldState = oldStates.getOrDefault(fieldKey, FieldState.HIDDEN);

      if (!oldState.equals(newState)) {
        changes.put(fieldKey, Map.of(
            "previous", oldState.name(),
            "current", newState.name()));
      }
    }

    return changes;
  }

  private ProfileResponse toResponse(PatientRegistrationProfile profile,
      List<PatientRegistrationProfileField> fields) {
    var fieldResponses = fields.stream()
        .map(f -> new PatientRegistrationFieldResponse(
            f.getFieldKey(), f.getFieldLabel(), f.getFieldGroup(), f.getFieldState().name(),
            f.getFieldOrder()))
        .collect(Collectors.toList());

    // Include all supported fields in response
    var supportedFields = getAllSupportedFields();

    return new ProfileResponse(
        profile.getHospitalId(),
        profile.getProfileName(),
        profile.getVersion(),
        fieldResponses,
        supportedFields,
        profile.getUpdatedAt(),
        profile.getUpdatedBy());
  }

  /**
   * Returns all supported fields for the frontend to display
   */
  private List<PatientRegistrationFieldResponse> getAllSupportedFields() {
    List<PatientRegistrationFieldResponse> supported = new ArrayList<>();
    int order = 0;

    // Identity
    supported.add(new PatientRegistrationFieldResponse("FULL_NAME", "Full Name", "Identity", null, order++));
    supported.add(new PatientRegistrationFieldResponse("DATE_OF_BIRTH", "Date of Birth", "Identity", null, order++));
    supported.add(new PatientRegistrationFieldResponse("GENDER", "Gender", "Identity", null, order++));
    supported.add(new PatientRegistrationFieldResponse("PATIENT_CODE", "Patient Code", "Identity", null, order++));

    // Contact
    supported.add(new PatientRegistrationFieldResponse("MOBILE", "Mobile", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("EMAIL", "Email", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("ADDRESS", "Address", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("CITY", "City", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("STATE", "State", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("POSTAL_CODE", "Postal Code", "Contact", null, order++));
    supported.add(new PatientRegistrationFieldResponse("PREFERRED_LANGUAGE", "Preferred Language", "Contact", null, order++));

    // Emergency
    supported.add(new PatientRegistrationFieldResponse("EMERGENCY_CONTACT_NAME", "Contact Name", "Emergency", null, order++));
    supported.add(new PatientRegistrationFieldResponse("EMERGENCY_CONTACT_RELATIONSHIP", "Relationship", "Emergency", null, order++));
    supported.add(new PatientRegistrationFieldResponse("EMERGENCY_CONTACT_MOBILE", "Mobile", "Emergency", null, order++));

    // Insurance & ID
    supported.add(new PatientRegistrationFieldResponse("INSURANCE_PROVIDER", "Insurance Provider", "Insurance & ID", null, order++));
    supported.add(new PatientRegistrationFieldResponse("MEMBER_ID", "Member ID", "Insurance & ID", null, order++));
    supported.add(new PatientRegistrationFieldResponse("GOVERNMENT_ID_TYPE", "Government ID Type", "Insurance & ID", null, order++));
    supported.add(new PatientRegistrationFieldResponse("GOVERNMENT_ID_NUMBER", "Government ID Number", "Insurance & ID", null, order++));

    // Clinical
    supported.add(new PatientRegistrationFieldResponse("BLOOD_GROUP", "Blood Group", "Clinical", null, order++));
    supported.add(new PatientRegistrationFieldResponse("ALLERGIES", "Allergies", "Clinical", null, order++));
    supported.add(new PatientRegistrationFieldResponse("CONSENT_STATUS", "Consent Status", "Clinical", null, order++));
    supported.add(new PatientRegistrationFieldResponse("REFERRING_PHYSICIAN", "Referring Physician", "Clinical", null, order++));

    // Guardian
    supported.add(new PatientRegistrationFieldResponse("GUARDIAN_NAME", "Name", "Guardian", null, order++));
    supported.add(new PatientRegistrationFieldResponse("GUARDIAN_RELATIONSHIP", "Relationship", "Guardian", null, order++));
    supported.add(new PatientRegistrationFieldResponse("GUARDIAN_MOBILE", "Mobile", "Guardian", null, order));

    return supported;
  }
}
