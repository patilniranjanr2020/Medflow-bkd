package com.medflow.modules.settings.domain.repository;

import com.medflow.modules.settings.domain.entity.PatientRegistrationProfileField;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRegistrationProfileFieldRepository
    extends JpaRepository<PatientRegistrationProfileField, Long> {

  List<PatientRegistrationProfileField> findByProfileIdOrderByFieldOrder(Long profileId);

  Optional<PatientRegistrationProfileField> findByProfileIdAndFieldKey(Long profileId, String fieldKey);

  void deleteByProfileId(Long profileId);
}
