package com.medflow.modules.laboratory.api;

import com.medflow.modules.laboratory.api.request.CompleteLabOrderRequest;
import com.medflow.modules.laboratory.api.request.CreateLabOrderRequest;
import com.medflow.modules.laboratory.api.request.UpdateLabOrderRequest;
import com.medflow.modules.laboratory.api.response.LabOrderResponse;
import com.medflow.shared.api.PageResponse;

/** Public API of the Laboratory module. */
public interface LabOrderService {

  LabOrderResponse create(Long hospitalId, CreateLabOrderRequest request);

  LabOrderResponse findById(Long hospitalId, Long labOrderId);

  PageResponse<LabOrderResponse> search(Long hospitalId, String query, LabOrderStatus status,
      LabPriority priority, Long patientId, int page, int size);

  default PageResponse<LabOrderResponse> search(Long hospitalId, LabOrderStatus status,
      LabPriority priority, Long patientId, int page, int size) {
    return search(hospitalId, null, status, priority, patientId, page, size);
  }

  LabOrderResponse update(Long hospitalId, Long labOrderId, UpdateLabOrderRequest request);

  void delete(Long hospitalId, Long labOrderId);

  LabOrderResponse startProcessing(Long hospitalId, Long labOrderId);

  LabOrderResponse complete(Long hospitalId, Long labOrderId, CompleteLabOrderRequest request);

  LabOrderResponse cancel(Long hospitalId, Long labOrderId);

  /** Used by analytics for the "reports filed" KPI. */
  long countCompleted(Long hospitalId);
}
