package com.medflow.modules.laboratory.api.request;

import com.medflow.modules.laboratory.api.LabPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLabOrderRequest(
    @NotBlank @Size(max = 150) String testName,
    @NotNull LabPriority priority,
    Long doctorId,
    @Size(max = 2000) String resultSummary) {
}
