package com.medflow.modules.pharmacy.api;

import com.medflow.modules.pharmacy.api.request.AdjustStockRequest;
import com.medflow.modules.pharmacy.api.request.CreateMedicationRequest;
import com.medflow.modules.pharmacy.api.request.UpdateMedicationRequest;
import com.medflow.modules.pharmacy.api.response.MedicationResponse;
import com.medflow.shared.api.PageResponse;

/** Public API of the Pharmacy module. */
public interface MedicationService {

  MedicationResponse create(Long hospitalId, CreateMedicationRequest request);

  MedicationResponse findById(Long hospitalId, Long medicationId);

  MedicationResponse update(Long hospitalId, Long medicationId, UpdateMedicationRequest request);

  PageResponse<MedicationResponse> search(Long hospitalId, String query, String category,
      String stockStatus, boolean lowStockOnly, int page, int size);

  default PageResponse<MedicationResponse> search(Long hospitalId, String query,
      boolean lowStockOnly, int page, int size) {
    return search(hospitalId, query, null, null, lowStockOnly, page, size);
  }

  java.util.List<String> categories(Long hospitalId);

  void delete(Long hospitalId, Long medicationId);

  /** Positive delta restocks, negative delta dispenses; stock can never go below zero. */
  MedicationResponse adjustStock(Long hospitalId, Long medicationId, AdjustStockRequest request);
}
