package com.medflow.modules.settings.api.response;

import java.time.Instant;
import java.util.List;

public record ProfileResponse(
    Long hospitalId,
    String profileName,
    int version,
    List<PatientRegistrationFieldResponse> fields,
    List<PatientRegistrationFieldResponse> supportedFields,
    Instant appliedAt,
    Long appliedBy) {
}
