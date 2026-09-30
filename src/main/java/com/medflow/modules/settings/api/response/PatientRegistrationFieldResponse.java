package com.medflow.modules.settings.api.response;

import java.time.Instant;

public record PatientRegistrationFieldResponse(
    String fieldKey,
    String fieldLabel,
    String fieldGroup,
    String currentState,  // REQUIRED, OPTIONAL, HIDDEN
    int fieldOrder) {
}
