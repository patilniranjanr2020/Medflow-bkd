package com.medflow.modules.appointments.application;

import com.medflow.modules.appointments.api.AppointmentBookedEvent;
import com.medflow.modules.appointments.api.AppointmentMode;
import com.medflow.modules.appointments.api.AppointmentScheduleConflictEvent;
import com.medflow.modules.appointments.api.AppointmentService;
import com.medflow.modules.appointments.api.AppointmentStatus;
import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.appointments.api.DailyAppointmentCount;
import com.medflow.modules.appointments.api.request.BookAppointmentRequest;
import com.medflow.modules.appointments.api.request.RescheduleAppointmentRequest;
import com.medflow.modules.appointments.api.response.AppointmentResponse;
import com.medflow.modules.appointments.domain.entity.Appointment;
import com.medflow.modules.appointments.domain.repository.AppointmentRepository;
import com.medflow.modules.appointments.domain.repository.AppointmentSpecifications;
import com.medflow.modules.doctors.api.DoctorService;
import com.medflow.modules.doctors.api.DoctorSummary;
import com.medflow.modules.patients.api.PatientService;
import com.medflow.modules.patients.api.PatientSummary;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.exception.ResourceNotFoundException;
import com.medflow.shared.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
class AppointmentServiceImpl implements AppointmentService {

  private static final int DEFAULT_DURATION_MINUTES = 30;
  private static final int MAX_PAGE_SIZE = 100;

  private final AppointmentRepository repository;
  private final PatientService patientService;
  private final DoctorService doctorService;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditService auditService;

  AppointmentServiceImpl(AppointmentRepository repository, PatientService patientService,
      DoctorService doctorService, ApplicationEventPublisher eventPublisher, AuditService auditService) {
    this.repository = repository;
    this.patientService = patientService;
    this.doctorService = doctorService;
    this.eventPublisher = eventPublisher;
    this.auditService = auditService;
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public AppointmentResponse book(Long hospitalId, Long bookedByUserId,
      BookAppointmentRequest request) {
    var patient = requirePatient(hospitalId, request.patientId());
    var doctor = requireDoctor(hospitalId, request.doctorId());
    var duration = request.durationMinutes() != null
        ? request.durationMinutes()
        : DEFAULT_DURATION_MINUTES;
    var mode = request.appointmentMode() != null ? request.appointmentMode() : AppointmentMode.ONLINE;

    var candidate = new Appointment(hospitalId, doctor.id(), patient.id(), mode,
        request.scheduledAt(), duration, nextQueueNumber(doctor.id(), request.scheduledAt()),
        bookedByUserId, request.reason(), doctor.consultationFee(), request.notes());

    if (hasConflict(candidate)) {
      eventPublisher.publishEvent(new AppointmentScheduleConflictEvent(hospitalId, doctor.id(),
          doctor.fullName(), candidate.getScheduledAt()));
      throw new BusinessRuleViolationException("The doctor already has an appointment in this time slot");
    }
    var appointment = repository.save(candidate);
    eventPublisher.publishEvent(new AppointmentBookedEvent(hospitalId, appointment.getId(),
        patient.id(), patient.fullName(), doctor.id(), doctor.fullName(),
        appointment.getScheduledAt()));
    auditService.record("BOOK", "APPOINTMENT", appointment.getId(), "{\"status\":\"booked\"}");

    return toResponse(appointment, patient.fullName(), doctor);
  }

  @Override
  @Transactional(readOnly = true)
  public AppointmentResponse findById(Long hospitalId, Long appointmentId) {
    return enrich(hospitalId, List.of(load(hospitalId, appointmentId))).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AppointmentResponse> search(Long hospitalId, AppointmentStatus status,
      Long doctorId, Long patientId, LocalDate date, int page, int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.ASC, "scheduledAt"));
    var result = repository.findAll(
        AppointmentSpecifications.withFilters(hospitalId, status, doctorId, patientId, date),
        pageable);
    return new PageResponse<>(enrich(hospitalId, result.getContent()), result.getNumber(),
        result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Override
  @Transactional
  public AppointmentResponse transition(Long hospitalId, Long appointmentId,
      AppointmentStatus target) {
    var appointment = load(hospitalId, appointmentId);
    appointment.transitionTo(target);
    if (target == AppointmentStatus.CANCELLED) {
      auditService.record("CANCEL", "APPOINTMENT", appointment.getId(), "{\"status\":\"cancelled\"}");
    }
    return enrich(hospitalId, List.of(appointment)).getFirst();
  }

  @Override
  @Transactional
  public AppointmentResponse reschedule(Long hospitalId, Long appointmentId,
      RescheduleAppointmentRequest request) {
    var appointment = load(hospitalId, appointmentId);
    appointment.reschedule(request.newScheduledAt());
    if (hasConflict(appointment)) {
      var doctor = requireDoctor(hospitalId, appointment.getDoctorId());
      eventPublisher.publishEvent(new AppointmentScheduleConflictEvent(hospitalId, doctor.id(),
          doctor.fullName(), appointment.getScheduledAt()));
      throw new BusinessRuleViolationException("The doctor already has an appointment in this time slot");
    }
    return enrich(hospitalId, List.of(appointment)).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public long countOnDate(Long hospitalId, LocalDate date) {
    var start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    var end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return repository.countByHospitalIdAndScheduledAtBetweenAndStatusNot(hospitalId, start, end,
        AppointmentStatus.CANCELLED);
  }

  @Override
  @Transactional(readOnly = true)
  public long countActive(Long hospitalId) {
    return repository.countByHospitalIdAndStatusIn(hospitalId,
        EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_CONSULTATION));
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal completedRevenueBetween(Long hospitalId, Instant from, Instant to) {
    return repository.sumCompletedFeesBetween(hospitalId, from, to);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DailyAppointmentCount> dailyCounts(Long hospitalId, LocalDate from, LocalDate to) {
    var start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    var end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    var byDay = repository.countDailyBetween(hospitalId, start, end).stream()
        .collect(Collectors.toMap(AppointmentRepository.DailyCountProjection::getVisitDay,
            AppointmentRepository.DailyCountProjection::getTotal));
    var series = new ArrayList<DailyAppointmentCount>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
      series.add(new DailyAppointmentCount(day, byDay.getOrDefault(day, 0L)));
    }
    return series;
  }

  /** Queue numbers restart every day, per doctor, so the waiting room list reads 1, 2, 3… */
  private Integer nextQueueNumber(Long doctorId, Instant scheduledAt) {
    var day = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();
    var start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
    var end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return repository.highestQueueNumber(doctorId, start, end) + 1;
  }

  /** Overlap scan is bounded to the surrounding day, so the in-memory check stays cheap. */
  private boolean hasConflict(Appointment candidate) {
    var windowStart = candidate.getScheduledAt().minusSeconds(24L * 3600);
    var windowEnd = candidate.endsAt().plusSeconds(24L * 3600);
    return repository
        .findByDoctorIdAndScheduledAtBetween(candidate.getDoctorId(), windowStart, windowEnd)
        .stream()
        .filter(existing -> !existing.getId().equals(candidate.getId()))
        .anyMatch(existing -> existing.overlaps(candidate.getScheduledAt(), candidate.endsAt()));
  }

  private List<AppointmentResponse> enrich(Long hospitalId, List<Appointment> appointments) {
    Map<Long, String> patientNames = patientService.summariesByIds(hospitalId,
            appointments.stream().map(Appointment::getPatientId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
    Map<Long, DoctorSummary> doctors = doctorService.summariesByIds(hospitalId,
            appointments.stream().map(Appointment::getDoctorId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(DoctorSummary::id, doctor -> doctor));

    return appointments.stream()
        .map(appointment -> toResponse(appointment,
            patientNames.getOrDefault(appointment.getPatientId(), "Unknown patient"),
            doctors.get(appointment.getDoctorId())))
        .toList();
  }

  private AppointmentResponse toResponse(Appointment appointment, String patientName,
      DoctorSummary doctor) {
    return new AppointmentResponse(appointment.getId(), appointment.getHospitalId(),
        appointment.getPatientId(), patientName, appointment.getDoctorId(),
        doctor == null ? "Unknown doctor" : doctor.fullName(),
        doctor == null ? null : doctor.specialty(),
        appointment.getAppointmentMode(), appointment.getScheduledAt(),
        appointment.getDurationMinutes(), appointment.getStatus(), appointment.getQueueNumber(),
        appointment.getBookedByUserId(), appointment.getReason(), appointment.getConsultationFee(),
        appointment.getNotes(), appointment.getCreatedAt(), appointment.getUpdatedAt());
  }

  private PatientSummary requirePatient(Long hospitalId, Long patientId) {
    return patientService.summariesByIds(hospitalId, List.of(patientId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
  }

  private DoctorSummary requireDoctor(Long hospitalId, Long doctorId) {
    return doctorService.summariesByIds(hospitalId, List.of(doctorId)).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
  }

  private Appointment load(Long hospitalId, Long appointmentId) {
    return repository.findByIdAndHospitalId(appointmentId, hospitalId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Appointment not found: " + appointmentId));
  }
}
