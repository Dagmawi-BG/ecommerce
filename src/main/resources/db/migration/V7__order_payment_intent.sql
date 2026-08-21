-- ============================================================================
-- V7 - Stripe payment intent reference on orders (ported from shopify-plus).
-- Unique, but nullable: Postgres allows many NULLs in a UNIQUE column.
-- ============================================================================
ALTER TABLE orders ADD COLUMN payment_intent_id VARCHAR(255) UNIQUE;
