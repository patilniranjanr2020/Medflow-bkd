package com.medflow.modules.settings.api;

import java.util.List;

/** Public settings-module boundary used by patient registration validation. */
public interface PatientProfileResolver {

  EffectiveProfile resolveProfile(Long hospitalId);

  void invalidateCache(Long hospitalId);

  record EffectiveProfile(Long profileId, String profileName, List<FieldConfig> visibleFields) {
    public boolean isFieldRequired(String fieldKey) {
      return visibleFields.stream().filter(field -> field.fieldKey().equals(fieldKey))
          .findFirst().map(FieldConfig::required).orElse(false);
    }

    public boolean isFieldVisible(String fieldKey) {
      return visibleFields.stream().anyMatch(field -> field.fieldKey().equals(fieldKey));
    }
  }

  record FieldConfig(String fieldKey, String fieldLabel, String fieldGroup,
                     boolean required, int displayOrder) {
  }
}
