package com.medflow.modules.settings.application;

import com.medflow.modules.audit.api.AuditService;
import com.medflow.modules.settings.api.PatientRegistrationProfileChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
/**
 * Listens for patient registration profile changes and records them in the audit trail.
 * Runs in a separate transaction so audit failures don't rollback the profile update.
 */
@Component
class ProfileChangedAuditListener {

  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  ProfileChangedAuditListener(AuditService auditService, ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  /**
   * Records the profile change in the audit trail.
   * Executes in a separate transaction (REQUIRES_NEW) so failures don't affect the main update.
   */
  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onProfileChanged(PatientRegistrationProfileChangedEvent event) {
    try {
      String metadata = objectMapper.writeValueAsString(
          Map.of(
              "profileId", event.profileId(),
              "fieldStateChanges", event.changes(),
              "changedAt", event.changedAt().toString()));

      auditService.record(
          "UPDATE_PATIENT_REGISTRATION_PROFILE",
          "PatientRegistrationProfile",
          event.profileId(),
          metadata);
    } catch (Exception e) {
      // Log but don't fail the main transaction
      // (actual logger would be injected)
      System.err.println("Failed to audit profile change: " + e.getMessage());
    }
  }
}
