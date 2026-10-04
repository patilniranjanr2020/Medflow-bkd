package com.medflow.modules.pharmacy.domain.entity;

import com.medflow.shared.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "medications")
public class Medication {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 100)
  private String category;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "stock_quantity", nullable = false)
  private int stockQuantity;

  @Column(name = "reorder_level", nullable = false)
  private int reorderLevel;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  protected Medication() {
  }

  public Medication(Long hospitalId, String name, String category, BigDecimal unitPrice,
      int stockQuantity, int reorderLevel, LocalDate expiryDate) {
    this.hospitalId = hospitalId;
    this.name = name;
    this.category = category;
    this.unitPrice = unitPrice;
    this.stockQuantity = stockQuantity;
    this.reorderLevel = reorderLevel;
    this.expiryDate = expiryDate;
    this.deleted = false;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(String name, String category, BigDecimal unitPrice, int reorderLevel,
      LocalDate expiryDate) {
    this.name = name;
    this.category = category;
    this.unitPrice = unitPrice;
    this.reorderLevel = reorderLevel;
    this.expiryDate = expiryDate;
    touch();
  }

  /** @return true when this adjustment crossed the reorder threshold downwards. */
  public boolean adjustStock(int delta) {
    var newQuantity = stockQuantity + delta;
    if (newQuantity < 0) {
      throw new BusinessRuleViolationException(
          "Insufficient stock: " + stockQuantity + " unit(s) of " + name + " available");
    }
    var wasAboveThreshold = stockQuantity > reorderLevel;
    this.stockQuantity = newQuantity;
    touch();
    return wasAboveThreshold && isLowStock();
  }

  public boolean isLowStock() {
    return stockQuantity <= reorderLevel;
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Long getHospitalId() { return hospitalId; }
  public String getName() { return name; }
  public String getCategory() { return category; }
  public BigDecimal getUnitPrice() { return unitPrice; }
  public int getStockQuantity() { return stockQuantity; }
  public int getReorderLevel() { return reorderLevel; }
  public LocalDate getExpiryDate() { return expiryDate; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void markDeleted() {
    this.deleted = true;
    touch();
  }

  public boolean isDeleted() { return deleted; }
}
