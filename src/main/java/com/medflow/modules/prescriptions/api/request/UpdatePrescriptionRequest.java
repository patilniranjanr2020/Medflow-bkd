package com.medflow.modules.prescriptions.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePrescriptionRequest(
    @NotNull Long patientId,
    @NotNull Long doctorId,
    Long appointmentId,
    @Size(max = 2000) String diagnosis,
    /** Signing stamps the record; unsigned prescriptions remain drafts. */
    Boolean digitallySigned,
    @NotEmpty List<@Valid PrescriptionItemRequest> medicines) {
}
