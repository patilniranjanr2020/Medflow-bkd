-- =====================================================================================
-- Migration V105: Add soft delete support to laboratory orders
-- =====================================================================================

ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_lab_orders_hospital_deleted ON lab_orders (hospital_id, is_deleted);
