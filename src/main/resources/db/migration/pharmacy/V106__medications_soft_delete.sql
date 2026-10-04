-- =====================================================================================
-- Migration V106: Add soft delete support to medications inventory
-- =====================================================================================

ALTER TABLE medications ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_medications_hospital_deleted ON medications (hospital_id, is_deleted);
