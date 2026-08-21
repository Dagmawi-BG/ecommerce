-- ============================================================================
-- V6 - Stock reservation window on orders (ported from shopify-plus).
-- A pending order holds its stock until paid; a scheduler releases it if the
-- reservation lapses. NULL = no active reservation (paid/cancelled/settled).
-- ============================================================================
ALTER TABLE orders ADD COLUMN reservation_expires_at TIMESTAMPTZ;

CREATE INDEX idx_orders_reservation ON orders (status, reservation_expires_at);
