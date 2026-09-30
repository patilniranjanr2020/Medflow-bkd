package com.medflow.modules.settings.domain.repository;

import com.medflow.modules.settings.domain.entity.PatientRegistrationProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRegistrationProfileRepository extends JpaRepository<PatientRegistrationProfile, Long> {

  Optional<PatientRegistrationProfile> findByHospitalIdAndActive(Long hospitalId, boolean active);

  Optional<PatientRegistrationProfile> findByHospitalIdAndId(Long hospitalId, Long id);
}
