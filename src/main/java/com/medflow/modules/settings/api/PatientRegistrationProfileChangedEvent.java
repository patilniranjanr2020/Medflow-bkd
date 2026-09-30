package com.medflow.modules.settings.api;

import java.time.Instant;
import java.util.Map;

/**
 * Published after a successful patient registration profile change.
 * Carries the tenant and change details so listeners can audit without lookups.
 */
public record PatientRegistrationProfileChangedEvent(
    Long hospitalId,
    Long profileId,
    Map<String, Map<String, String>> changes,  // fieldKey -> {previous: state, current: state}
    Long changedBy,
    Instant changedAt) {
}
