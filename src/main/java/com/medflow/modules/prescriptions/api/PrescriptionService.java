package com.medflow.modules.prescriptions.api;

import com.medflow.modules.prescriptions.api.request.CreatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.request.UpdatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.response.PrescriptionResponse;
import com.medflow.shared.api.PageResponse;

/** Public API of the Prescriptions module. */
public interface PrescriptionService {

  PrescriptionResponse create(Long hospitalId, CreatePrescriptionRequest request);

  PrescriptionResponse findById(Long hospitalId, Long prescriptionId);

  PageResponse<PrescriptionResponse> search(Long hospitalId, Long patientId, Long doctorId,
      PrescriptionStatus status, int page, int size);

  PrescriptionResponse update(Long hospitalId, Long prescriptionId, UpdatePrescriptionRequest request);

  void delete(Long hospitalId, Long prescriptionId);

  PrescriptionResponse complete(Long hospitalId, Long prescriptionId);

  PrescriptionResponse cancel(Long hospitalId, Long prescriptionId);

  long countByHospital(Long hospitalId);
}
