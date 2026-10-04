package com.medflow.modules.laboratory.application;

import com.medflow.modules.doctors.api.response.DoctorResponse;
import com.medflow.modules.doctors.api.DoctorService;
import com.medflow.modules.doctors.api.DoctorSummary;
import com.medflow.modules.laboratory.api.LabOrderCompletedEvent;
import com.medflow.modules.laboratory.api.LabOrderService;
import com.medflow.modules.laboratory.api.LabOrderStatus;
import com.medflow.modules.laboratory.api.LabPriority;
import com.medflow.modules.laboratory.api.request.CompleteLabOrderRequest;
import com.medflow.modules.laboratory.api.request.CreateLabOrderRequest;
import com.medflow.modules.laboratory.api.request.UpdateLabOrderRequest;
import com.medflow.modules.laboratory.api.response.LabOrderResponse;
import com.medflow.modules.laboratory.domain.entity.LabOrder;
import com.medflow.modules.laboratory.domain.repository.LabOrderRepository;
import com.medflow.modules.laboratory.domain.repository.LabOrderSpecifications;
import com.medflow.modules.patients.api.PatientService;
import com.medflow.modules.patients.api.PatientSummary;
import com.medflow.modules.patients.api.response.PatientResponse;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LabOrderServiceImpl implements LabOrderService {

  private static final int MAX_PAGE_SIZE = 100;

  private final LabOrderRepository repository;
  private final PatientService patientService;
  private final DoctorService doctorService;
  private final ApplicationEventPublisher eventPublisher;

  LabOrderServiceImpl(LabOrderRepository repository, PatientService patientService,
      DoctorService doctorService, ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.patientService = patientService;
    this.doctorService = doctorService;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public LabOrderResponse create(Long hospitalId, CreateLabOrderRequest request) {
    var patient = requirePatient(hospitalId, request.patientId());
    var doctor = requireDoctor(hospitalId, request.doctorId());
    var labOrder = repository.save(new LabOrder(hospitalId, patient.id(), doctor.id(),
        request.testName(), request.priority()));
    return toResponse(labOrder, patient.fullName(), doctor.fullName());
  }

  @Override
  @Transactional(readOnly = true)
  public LabOrderResponse findById(Long hospitalId, Long labOrderId) {
    return enrich(hospitalId, List.of(load(hospitalId, labOrderId))).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<LabOrderResponse> search(Long hospitalId, String query, LabOrderStatus status,
      LabPriority priority, Long patientId, int page, int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, "orderedAt"));

    List<Long> matchingPatientIds = null;
    List<Long> matchingDoctorIds = null;

    if (query != null && !query.isBlank()) {
      var trimmed = query.trim();
      try {
        matchingPatientIds = patientService.search(hospitalId, trimmed, null, 0, 100).content()
            .stream().map(PatientResponse::id).toList();
      } catch (Exception ignored) {
      }
      try {
        matchingDoctorIds = doctorService.search(hospitalId, trimmed, null, null, 0, 100).content()
            .stream().map(DoctorResponse::id).toList();
      } catch (Exception ignored) {
      }
    }

    var result = repository.findAll(
        LabOrderSpecifications.withFilters(hospitalId, query, status, priority, patientId,
            matchingPatientIds, matchingDoctorIds), pageable);
    return new PageResponse<>(enrich(hospitalId, result.getContent()), result.getNumber(),
        result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Override
  @Transactional
  public LabOrderResponse update(Long hospitalId, Long labOrderId, UpdateLabOrderRequest request) {
    var labOrder = load(hospitalId, labOrderId);
    if (request.doctorId() != null) {
      requireDoctor(hospitalId, request.doctorId());
    }
    labOrder.updateDetails(request.testName(), request.priority(), request.doctorId());
    if (request.resultSummary() != null) {
      labOrder.updateResult(request.resultSummary());
    }
    return enrich(hospitalId, List.of(labOrder)).getFirst();
  }

  @Override
  @Transactional
  public void delete(Long hospitalId, Long labOrderId) {
    var labOrder = load(hospitalId, labOrderId);
    labOrder.markDeleted();
  }

  @Override
  @Transactional
  public LabOrderResponse startProcessing(Long hospitalId, Long labOrderId) {
    var labOrder = load(hospitalId, labOrderId);
    labOrder.startProcessing();
    return enrich(hospitalId, List.of(labOrder)).getFirst();
  }

  @Override
  @Transactional
  public LabOrderResponse complete(Long hospitalId, Long labOrderId,
      CompleteLabOrderRequest request) {
    var labOrder = load(hospitalId, labOrderId);
    labOrder.complete(request.resultSummary());
    var patientName = patientService
        .summariesByIds(hospitalId, List.of(labOrder.getPatientId())).stream()
        .findFirst().map(PatientSummary::fullName).orElse("Unknown patient");
    eventPublisher.publishEvent(new LabOrderCompletedEvent(hospitalId, labOrder.getId(),
        labOrder.getPatientId(), patientName, labOrder.getTestName()));
    return enrich(hospitalId, List.of(labOrder)).getFirst();
  }

  @Override
  @Transactional
  public LabOrderResponse cancel(Long hospitalId, Long labOrderId) {
    var labOrder = load(hospitalId, labOrderId);
    labOrder.cancel();
    return enrich(hospitalId, List.of(labOrder)).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public long countCompleted(Long hospitalId) {
    return repository.countByHospitalIdAndStatusAndDeletedFalse(hospitalId, LabOrderStatus.COMPLETED);
  }

  private List<LabOrderResponse> enrich(Long hospitalId, List<LabOrder> labOrders) {
    Map<Long, String> patientNames = patientService.summariesByIds(hospitalId,
            labOrders.stream().map(LabOrder::getPatientId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
    Map<Long, String> doctorNames = doctorService.summariesByIds(hospitalId,
            labOrders.stream().map(LabOrder::getDoctorId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(DoctorSummary::id, DoctorSummary::fullName));
    return labOrders.stream()
        .map(labOrder -> toResponse(labOrder,
            patientNames.getOrDefault(labOrder.getPatientId(), "Unknown patient"),
            doctorNames.getOrDefault(labOrder.getDoctorId(), "Unknown doctor")))
        .toList();
  }

  private LabOrderResponse toResponse(LabOrder labOrder, String patientName, String doctorName) {
    return new LabOrderResponse(labOrder.getId(), labOrder.getHospitalId(),
        labOrder.getPatientId(), patientName, labOrder.getDoctorId(), doctorName,
        labOrder.getTestName(), labOrder.getPriority(), labOrder.getStatus(),
        labOrder.getResultSummary(), labOrder.getOrderedAt(), labOrder.getCompletedAt(),
        labOrder.getUpdatedAt());
  }

  private PatientSummary requirePatient(Long hospitalId, Long patientId) {
    return patientService.summariesByIds(hospitalId, List.of(patientId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
  }

  private DoctorSummary requireDoctor(Long hospitalId, Long doctorId) {
    return doctorService.summariesByIds(hospitalId, List.of(doctorId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
  }

  private LabOrder load(Long hospitalId, Long labOrderId) {
    return repository.findByIdAndHospitalIdAndDeletedFalse(labOrderId, hospitalId)
        .orElseThrow(() -> new ResourceNotFoundException("Lab order not found: " + labOrderId));
  }
}
