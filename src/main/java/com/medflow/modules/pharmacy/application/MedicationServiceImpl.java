package com.medflow.modules.pharmacy.application;

import com.medflow.modules.pharmacy.api.MedicationLowStockEvent;
import com.medflow.modules.pharmacy.api.MedicationService;
import com.medflow.modules.pharmacy.api.request.AdjustStockRequest;
import com.medflow.modules.pharmacy.api.request.CreateMedicationRequest;
import com.medflow.modules.pharmacy.api.request.UpdateMedicationRequest;
import com.medflow.modules.pharmacy.api.response.MedicationResponse;
import com.medflow.modules.pharmacy.domain.entity.Medication;
import com.medflow.modules.pharmacy.domain.repository.MedicationRepository;
import com.medflow.shared.api.PageResponse;
import com.medflow.shared.exception.DuplicateResourceException;
import com.medflow.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MedicationServiceImpl implements MedicationService {

  private static final int MAX_PAGE_SIZE = 100;

  private final MedicationRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  MedicationServiceImpl(MedicationRepository repository,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public MedicationResponse create(Long hospitalId, CreateMedicationRequest request) {
    if (repository.existsByHospitalIdAndNameIgnoreCaseAndDeletedFalse(hospitalId, request.name())) {
      throw new DuplicateResourceException("A medication with this name already exists");
    }
    var medication = repository.save(new Medication(hospitalId, request.name(), request.category(),
        request.unitPrice(), request.stockQuantity(), request.reorderLevel(),
        request.expiryDate()));
    return toResponse(medication);
  }

  @Override
  @Transactional(readOnly = true)
  public MedicationResponse findById(Long hospitalId, Long medicationId) {
    return toResponse(load(hospitalId, medicationId));
  }

  @Override
  @Transactional
  public MedicationResponse update(Long hospitalId, Long medicationId,
      UpdateMedicationRequest request) {
    if (repository.existsByHospitalIdAndNameIgnoreCaseAndIdNotAndDeletedFalse(hospitalId, request.name(),
        medicationId)) {
      throw new DuplicateResourceException("A medication with this name already exists");
    }
    var medication = load(hospitalId, medicationId);
    medication.update(request.name(), request.category(), request.unitPrice(),
        request.reorderLevel(), request.expiryDate());
    return toResponse(medication);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<MedicationResponse> search(Long hospitalId, String query, String category,
      String stockStatus, boolean lowStockOnly, int page, int size) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.ASC, "name"));
    var normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
    var normalizedCategory = (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) ? null : category.trim();
    var normalizedStockStatus = (stockStatus == null || stockStatus.isBlank()) ? null : stockStatus.trim().toUpperCase();
    return PageResponse.from(
        repository.search(hospitalId, normalizedQuery, normalizedCategory, normalizedStockStatus, lowStockOnly, pageable)
            .map(this::toResponse));
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<String> categories(Long hospitalId) {
    return repository.findDistinctCategories(hospitalId);
  }

  @Override
  @Transactional
  public void delete(Long hospitalId, Long medicationId) {
    var medication = load(hospitalId, medicationId);
    medication.markDeleted();
  }

  @Override
  @Transactional
  public MedicationResponse adjustStock(Long hospitalId, Long medicationId,
      AdjustStockRequest request) {
    var medication = load(hospitalId, medicationId);
    if (medication.adjustStock(request.delta())) {
      eventPublisher.publishEvent(new MedicationLowStockEvent(hospitalId, medication.getId(),
          medication.getName(), medication.getStockQuantity(), medication.getReorderLevel()));
    }
    return toResponse(medication);
  }

  private Medication load(Long hospitalId, Long medicationId) {
    return repository.findByIdAndHospitalIdAndDeletedFalse(medicationId, hospitalId)
        .orElseThrow(() -> new ResourceNotFoundException("Medication not found: " + medicationId));
  }

  private MedicationResponse toResponse(Medication medication) {
    return new MedicationResponse(medication.getId(), medication.getHospitalId(),
        medication.getName(), medication.getCategory(), medication.getUnitPrice(),
        medication.getStockQuantity(), medication.getReorderLevel(), medication.isLowStock(),
        medication.getExpiryDate(), medication.getCreatedAt(), medication.getUpdatedAt());
  }
}
