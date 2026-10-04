package com.medflow.modules.prescriptions.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medflow.modules.doctors.api.DoctorService;
import com.medflow.modules.doctors.api.DoctorSummary;
import com.medflow.modules.patients.api.PatientService;
import com.medflow.modules.patients.api.PatientSummary;
import com.medflow.modules.prescriptions.api.PrescriptionService;
import com.medflow.modules.prescriptions.api.PrescriptionStatus;
import com.medflow.modules.prescriptions.api.request.CreatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.request.UpdatePrescriptionRequest;
import com.medflow.modules.prescriptions.api.response.PrescriptionItemResponse;
import com.medflow.modules.prescriptions.api.response.PrescriptionResponse;
import com.medflow.modules.prescriptions.domain.entity.Prescription;
import com.medflow.modules.prescriptions.domain.repository.PrescriptionRepository;
import com.medflow.modules.prescriptions.domain.repository.PrescriptionSpecifications;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.exception.BusinessRuleViolationException;
import com.medflow.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PrescriptionServiceImpl implements PrescriptionService {

  private static final Logger log = LoggerFactory.getLogger(PrescriptionServiceImpl.class);
  private static final int MAX_PAGE_SIZE = 100;
  private static final TypeReference<List<PrescriptionItemResponse>> ITEM_LIST =
      new TypeReference<>() { };

  private final PrescriptionRepository repository;
  private final PatientService patientService;
  private final DoctorService doctorService;
  private final ObjectMapper objectMapper;

  PrescriptionServiceImpl(PrescriptionRepository repository, PatientService patientService,
      DoctorService doctorService, ObjectMapper objectMapper) {
    this.repository = repository;
    this.patientService = patientService;
    this.doctorService = doctorService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public PrescriptionResponse create(Long hospitalId, CreatePrescriptionRequest request) {
    var patient = requirePatient(hospitalId, request.patientId());
    var doctor = requireDoctor(hospitalId, request.doctorId());

    var medicines = request.medicines().stream()
        .map(item -> new PrescriptionItemResponse(item.medicationName(), item.dosage(),
            item.frequency(), item.durationDays(), item.instructions()))
        .toList();

    var prescription = repository.save(new Prescription(hospitalId, request.appointmentId(),
        doctor.id(), patient.id(), request.diagnosis(), writeMedicines(medicines),
        !Boolean.FALSE.equals(request.digitallySigned())));

    return toResponse(prescription, patient.fullName(), doctor.fullName());
  }

  @Override
  @Transactional(readOnly = true)
  public PrescriptionResponse findById(Long hospitalId, Long prescriptionId) {
    return enrich(hospitalId, List.of(load(hospitalId, prescriptionId))).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<PrescriptionResponse> search(Long hospitalId, Long patientId, Long doctorId,
      PrescriptionStatus status, int page, int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    var result = repository.findAll(
        PrescriptionSpecifications.withFilters(hospitalId, patientId, doctorId, status), pageable);
    return new PageResponse<>(enrich(hospitalId, result.getContent()), result.getNumber(),
        result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Override
  @Transactional
  public PrescriptionResponse update(Long hospitalId, Long prescriptionId,
      UpdatePrescriptionRequest request) {
    var prescription = load(hospitalId, prescriptionId);
    var patient = requirePatient(hospitalId, request.patientId());
    var doctor = requireDoctor(hospitalId, request.doctorId());

    var medicines = request.medicines().stream()
        .map(item -> new PrescriptionItemResponse(item.medicationName(), item.dosage(),
            item.frequency(), item.durationDays(), item.instructions()))
        .toList();

    prescription.update(doctor.id(), patient.id(), request.appointmentId(),
        request.diagnosis(), writeMedicines(medicines), request.digitallySigned());

    return toResponse(prescription, patient.fullName(), doctor.fullName());
  }

  @Override
  @Transactional
  public void delete(Long hospitalId, Long prescriptionId) {
    var prescription = load(hospitalId, prescriptionId);
    if (prescription.isDigitallySigned()) {
      throw new BusinessRuleViolationException("A digitally signed prescription cannot be deleted");
    }
    if (prescription.getStatus() != PrescriptionStatus.ACTIVE) {
      throw new BusinessRuleViolationException("Only active draft prescriptions can be deleted");
    }
    repository.delete(prescription);
  }

  @Override
  @Transactional
  public PrescriptionResponse complete(Long hospitalId, Long prescriptionId) {
    var prescription = load(hospitalId, prescriptionId);
    prescription.complete();
    return enrich(hospitalId, List.of(prescription)).getFirst();
  }

  @Override
  @Transactional
  public PrescriptionResponse cancel(Long hospitalId, Long prescriptionId) {
    var prescription = load(hospitalId, prescriptionId);
    prescription.cancel();
    return enrich(hospitalId, List.of(prescription)).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByHospital(Long hospitalId) {
    return repository.countByHospitalId(hospitalId);
  }

  private List<PrescriptionResponse> enrich(Long hospitalId, List<Prescription> prescriptions) {
    Map<Long, String> patientNames = patientService.summariesByIds(hospitalId,
            prescriptions.stream().map(Prescription::getPatientId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
    Map<Long, String> doctorNames = doctorService.summariesByIds(hospitalId,
            prescriptions.stream().map(Prescription::getDoctorId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(DoctorSummary::id, DoctorSummary::fullName));

    return prescriptions.stream()
        .map(prescription -> toResponse(prescription,
            patientNames.getOrDefault(prescription.getPatientId(), "Unknown patient"),
            doctorNames.getOrDefault(prescription.getDoctorId(), "Unknown doctor")))
        .toList();
  }

  private String writeMedicines(List<PrescriptionItemResponse> medicines) {
    try {
      return objectMapper.writeValueAsString(medicines);
    } catch (JsonProcessingException exception) {
      throw new BusinessRuleViolationException("Could not store the medication list");
    }
  }

  /**
   * A stored prescription must always render, even if the JSON was written by an older
   * version of the schema — a clinical record that fails to display is worse than one
   * that displays without its lines.
   */
  private List<PrescriptionItemResponse> readMedicines(Prescription prescription) {
    try {
      return objectMapper.readValue(prescription.getMedicinesJson(), ITEM_LIST);
    } catch (JsonProcessingException exception) {
      log.warn("Unreadable medicines_json on prescription {}", prescription.getId(), exception);
      return List.of();
    }
  }

  private PrescriptionResponse toResponse(Prescription prescription, String patientName,
      String doctorName) {
    return new PrescriptionResponse(prescription.getId(), prescription.getHospitalId(),
        prescription.getAppointmentId(), prescription.getPatientId(), patientName,
        prescription.getDoctorId(), doctorName, prescription.getDiagnosis(),
        readMedicines(prescription), prescription.isDigitallySigned(), prescription.getSignedAt(),
        prescription.getStatus(), prescription.getCreatedAt(), prescription.getUpdatedAt());
  }

  private PatientSummary requirePatient(Long hospitalId, Long patientId) {
    return patientService.summariesByIds(hospitalId, List.of(patientId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
  }

  private DoctorSummary requireDoctor(Long hospitalId, Long doctorId) {
    return doctorService.summariesByIds(hospitalId, List.of(doctorId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
  }

  private Prescription load(Long hospitalId, Long prescriptionId) {
    return repository.findByIdAndHospitalId(prescriptionId, hospitalId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Prescription not found: " + prescriptionId));
  }
}
