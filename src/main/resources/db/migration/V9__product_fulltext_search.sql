-- ============================================================================
-- V9 - Full-text search over the product catalog.
-- A generated tsvector (name weighted above description) + GIN index gives
-- word-aware, ranked search; the immutable regconfig cast lets it be STORED.
-- ============================================================================
ALTER TABLE products
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english'::regconfig, coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B')
    ) STORED;

CREATE INDEX idx_products_search ON products USING GIN (search_vector);

-- Trigram index accelerates the ILIKE autocomplete lookups.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
