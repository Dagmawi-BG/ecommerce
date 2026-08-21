-- ============================================================================
-- V8 - Guest carts (ported from shopify-plus). A cart now belongs to EITHER a
-- registered user OR an anonymous guest (identified by a cookie token).
-- ============================================================================
ALTER TABLE carts ALTER COLUMN user_id DROP NOT NULL;      -- user_id keeps its UNIQUE (NULLs allowed)
ALTER TABLE carts ADD COLUMN guest_token VARCHAR(100) UNIQUE;
ALTER TABLE carts ADD CONSTRAINT chk_cart_owner
    CHECK (user_id IS NOT NULL OR guest_token IS NOT NULL);
