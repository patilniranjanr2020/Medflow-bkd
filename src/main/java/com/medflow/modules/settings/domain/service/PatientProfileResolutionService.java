package com.medflow.modules.settings.domain.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.medflow.modules.settings.api.PatientProfileResolver;
import com.medflow.modules.settings.domain.entity.PatientRegistrationProfileField.FieldState;
import com.medflow.modules.settings.domain.repository.PatientRegistrationProfileFieldRepository;
import com.medflow.modules.settings.domain.repository.PatientRegistrationProfileRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the effective profile used by patient registration validation. */
@Service
public class PatientProfileResolutionService implements PatientProfileResolver {

  private final PatientRegistrationProfileRepository profileRepository;
  private final PatientRegistrationProfileFieldRepository fieldRepository;
  private final Cache<Long, EffectiveProfile> cache;

  PatientProfileResolutionService(PatientRegistrationProfileRepository profileRepository,
      PatientRegistrationProfileFieldRepository fieldRepository) {
    this.profileRepository = profileRepository;
    this.fieldRepository = fieldRepository;
    this.cache = Caffeine.newBuilder().maximumSize(100)
        .expireAfterWrite(5, TimeUnit.MINUTES).build();
  }

  @Override
  @Transactional(readOnly = true)
  public EffectiveProfile resolveProfile(Long hospitalId) {
    return cache.get(hospitalId, this::loadProfile);
  }

  private EffectiveProfile loadProfile(Long hospitalId) {
    var profile = profileRepository.findByHospitalIdAndActive(hospitalId, true).orElse(null);
    if (profile == null) {
      return new EffectiveProfile(null, "Default", List.of(
          new FieldConfig("FULL_NAME", "Full Name", "Identity", false, 0),
          new FieldConfig("DATE_OF_BIRTH", "Date of Birth", "Identity", false, 1),
          new FieldConfig("GENDER", "Gender", "Identity", false, 2),
          new FieldConfig("MOBILE", "Mobile", "Contact", false, 3),
          new FieldConfig("EMAIL", "Email", "Contact", false, 4),
          new FieldConfig("ADDRESS", "Address", "Contact", false, 5),
          new FieldConfig("BLOOD_GROUP", "Blood Group", "Clinical", false, 6),
          new FieldConfig("EMERGENCY_CONTACT_NAME", "Emergency Contact Name", "Emergency", false, 7),
          new FieldConfig("EMERGENCY_CONTACT_MOBILE", "Emergency Contact Mobile", "Emergency", false, 8)));
    }

    var fields = fieldRepository.findByProfileIdOrderByFieldOrder(profile.getId()).stream()
        .filter(field -> field.getFieldState() != FieldState.HIDDEN)
        .map(field -> new FieldConfig(field.getFieldKey(), field.getFieldLabel(),
            field.getFieldGroup(), field.getFieldState() == FieldState.REQUIRED,
            field.getFieldOrder()))
        .collect(Collectors.toList());
    return new EffectiveProfile(profile.getId(), profile.getProfileName(), fields);
  }

  @Override
  public void invalidateCache(Long hospitalId) {
    cache.invalidate(hospitalId);
  }
}
