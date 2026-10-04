package com.medflow.modules.laboratory.domain.repository;

import com.medflow.modules.laboratory.api.LabOrderStatus;
import com.medflow.modules.laboratory.domain.entity.LabOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LabOrderRepository
    extends JpaRepository<LabOrder, Long>, JpaSpecificationExecutor<LabOrder> {

  Optional<LabOrder> findByIdAndHospitalId(Long id, Long hospitalId);

  Optional<LabOrder> findByIdAndHospitalIdAndDeletedFalse(Long id, Long hospitalId);

  long countByHospitalIdAndStatus(Long hospitalId, LabOrderStatus status);

  long countByHospitalIdAndStatusAndDeletedFalse(Long hospitalId, LabOrderStatus status);
}
