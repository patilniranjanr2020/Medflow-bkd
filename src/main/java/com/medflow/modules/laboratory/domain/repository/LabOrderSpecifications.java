package com.medflow.modules.laboratory.domain.repository;

import com.medflow.modules.laboratory.api.LabOrderStatus;
import com.medflow.modules.laboratory.api.LabPriority;
import com.medflow.modules.laboratory.domain.entity.LabOrder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic filters for the specimen queue; the tenant predicate is never optional. */
public final class LabOrderSpecifications {

  private LabOrderSpecifications() {
  }

  public static Specification<LabOrder> withFilters(Long hospitalId, LabOrderStatus status,
      LabPriority priority, Long patientId) {
    return withFilters(hospitalId, null, status, priority, patientId, null, null);
  }

  public static Specification<LabOrder> withFilters(Long hospitalId, String queryStr,
      LabOrderStatus status, LabPriority priority, Long patientId,
      Collection<Long> matchingPatientIds, Collection<Long> matchingDoctorIds) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(builder.equal(root.get("hospitalId"), hospitalId));
      predicates.add(builder.isFalse(root.get("deleted")));

      if (status != null) {
        predicates.add(builder.equal(root.get("status"), status));
      }
      if (priority != null) {
        predicates.add(builder.equal(root.get("priority"), priority));
      }
      if (patientId != null) {
        predicates.add(builder.equal(root.get("patientId"), patientId));
      }

      if (queryStr != null && !queryStr.isBlank()) {
        var lowerQuery = "%" + queryStr.trim().toLowerCase() + "%";
        var searchPredicates = new ArrayList<Predicate>();

        searchPredicates.add(builder.like(builder.lower(root.get("testName")), lowerQuery));
        searchPredicates.add(builder.like(
            builder.lower(builder.coalesce(root.get("resultSummary"), "")), lowerQuery));
        searchPredicates.add(builder.like(
            builder.lower(root.get("status").as(String.class)), lowerQuery));
        searchPredicates.add(builder.like(
            builder.lower(root.get("priority").as(String.class)), lowerQuery));

        if (matchingPatientIds != null && !matchingPatientIds.isEmpty()) {
          searchPredicates.add(root.get("patientId").in(matchingPatientIds));
        }
        if (matchingDoctorIds != null && !matchingDoctorIds.isEmpty()) {
          searchPredicates.add(root.get("doctorId").in(matchingDoctorIds));
        }

        predicates.add(builder.or(searchPredicates.toArray(Predicate[]::new)));
      }

      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
