-- ============================================================================
-- V5 - Coupons / discount codes (ported from shopify-plus)
-- ============================================================================
CREATE TABLE coupons (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(50) NOT NULL UNIQUE,          -- stored uppercase
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),
    amount        NUMERIC(12,2) NOT NULL CHECK (amount >= 0.00),
    expires_at    TIMESTAMPTZ,                          -- null = never expires
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_coupons_updated BEFORE UPDATE ON coupons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Cart remembers the applied code; it is re-validated live at every total.
ALTER TABLE carts ADD COLUMN coupon_code VARCHAR(50);

-- Orders snapshot the money breakdown at checkout.
ALTER TABLE orders ADD COLUMN subtotal_amount NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (discount_amount >= 0.00);
ALTER TABLE orders ADD COLUMN coupon_code     VARCHAR(50);

-- Backfill existing orders: no discount, subtotal == total.
UPDATE orders SET subtotal_amount = total_amount WHERE subtotal_amount IS NULL;
ALTER TABLE orders ALTER COLUMN subtotal_amount SET NOT NULL;
