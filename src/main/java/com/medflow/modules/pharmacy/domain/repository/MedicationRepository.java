package com.medflow.modules.pharmacy.domain.repository;

import com.medflow.modules.pharmacy.domain.entity.Medication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

  Optional<Medication> findByIdAndHospitalId(Long id, Long hospitalId);

  Optional<Medication> findByIdAndHospitalIdAndDeletedFalse(Long id, Long hospitalId);

  boolean existsByHospitalIdAndNameIgnoreCase(Long hospitalId, String name);

  boolean existsByHospitalIdAndNameIgnoreCaseAndDeletedFalse(Long hospitalId, String name);

  boolean existsByHospitalIdAndNameIgnoreCaseAndIdNot(Long hospitalId, String name, Long id);

  boolean existsByHospitalIdAndNameIgnoreCaseAndIdNotAndDeletedFalse(Long hospitalId, String name, Long id);

  @Query("""
      SELECT DISTINCT m.category FROM Medication m
      WHERE m.hospitalId = :hospitalId AND m.deleted = FALSE
      ORDER BY m.category ASC
      """)
  List<String> findDistinctCategories(@Param("hospitalId") Long hospitalId);

  @Query("""
      SELECT m FROM Medication m
      WHERE m.hospitalId = :hospitalId
        AND m.deleted = FALSE
        AND (CAST(:query AS string) IS NULL
             OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
             OR LOWER(m.category) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
        AND (CAST(:category AS string) IS NULL OR :category = '' OR LOWER(m.category) = LOWER(CAST(:category AS string)))
        AND (
             (CAST(:stockStatus AS string) IS NULL OR :stockStatus = '' OR :stockStatus = 'ALL')
             AND (:lowStockOnly = FALSE OR m.stockQuantity <= m.reorderLevel)
             OR (:stockStatus = 'LOW_STOCK' AND m.stockQuantity <= m.reorderLevel)
             OR (:stockStatus = 'IN_STOCK' AND m.stockQuantity > m.reorderLevel)
             OR (:stockStatus = 'OUT_OF_STOCK' AND m.stockQuantity = 0)
        )
      """)
  Page<Medication> search(
      @Param("hospitalId") Long hospitalId,
      @Param("query") String query,
      @Param("category") String category,
      @Param("stockStatus") String stockStatus,
      @Param("lowStockOnly") boolean lowStockOnly,
      Pageable pageable);
}
