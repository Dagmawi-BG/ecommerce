-- ============================================================================
-- V1 — Initial e-commerce schema (revised)
-- Incorporates: optimistic-lock version, order-item snapshots, generated
-- subtotal, currency, JSONB address snapshot, lifecycle timestamps,
-- DB-level updated_at triggers, and de-duplicated indexes.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Reusable trigger: keep updated_at accurate even for non-JPA writes.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- 1. USER PROFILES  (local mirror of Keycloak subjects)
-- ----------------------------------------------------------------------------
CREATE TABLE user_profiles (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_id     VARCHAR(255) NOT NULL UNIQUE,   -- JWT 'sub' claim
    email           VARCHAR(255) NOT NULL UNIQUE,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone_number    VARCHAR(30),
    shipping_street VARCHAR(255),
    shipping_city   VARCHAR(100),
    shipping_zip    VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_user_profiles_updated BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- 2. CATEGORIES
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- ----------------------------------------------------------------------------
-- 3. PRODUCTS
-- ----------------------------------------------------------------------------
CREATE TABLE products (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku            VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    price          NUMERIC(12,2) NOT NULL CHECK (price >= 0.00),
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    category_id    BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    image_url      VARCHAR(512),
    version        BIGINT NOT NULL DEFAULT 0,          -- @Version optimistic lock
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_products_updated BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- 4. CARTS & CART ITEMS  (one active cart per user)
-- ----------------------------------------------------------------------------
CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES user_profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_carts_updated BEFORE UPDATE ON carts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity   INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id)
);
CREATE TRIGGER trg_cart_items_updated BEFORE UPDATE ON cart_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- 5. ORDERS & ORDER ITEMS  (immutable checkout records)
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number     VARCHAR(64) NOT NULL UNIQUE,        -- e.g. ORD-20260818-8A9F
    user_id          BIGINT NOT NULL REFERENCES user_profiles(id) ON DELETE RESTRICT,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    currency         CHAR(3) NOT NULL DEFAULT 'USD',
    total_amount     NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0.00),
    shipping_address JSONB NOT NULL,                     -- structured snapshot
    paid_at          TIMESTAMPTZ,
    shipped_at       TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (
        status IN ('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED')
    )
);
CREATE TRIGGER trg_orders_updated BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_name VARCHAR(255) NOT NULL,                  -- snapshot at purchase
    product_sku  VARCHAR(50)  NOT NULL,                  -- snapshot at purchase
    quantity     INT NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0.00),
    subtotal     NUMERIC(12,2) GENERATED ALWAYS AS (quantity * unit_price) STORED
);

-- ----------------------------------------------------------------------------
-- 6. INDEXES
-- UNIQUE constraints already create indexes, so only non-unique lookup columns
-- are indexed here (sku, keycloak_id, cart_id prefix are covered elsewhere).
-- ----------------------------------------------------------------------------
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active   ON products(is_active);
CREATE INDEX idx_orders_user       ON orders(user_id);
CREATE INDEX idx_orders_status     ON orders(status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
