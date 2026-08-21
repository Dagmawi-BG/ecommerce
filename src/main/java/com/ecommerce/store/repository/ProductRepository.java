package com.ecommerce.store.repository;

import com.ecommerce.store.dto.response.ProductSuggestion;
import com.ecommerce.store.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrue();

    Optional<Product> findBySku(String sku);

    /**
     * Active-catalog search with optional category (by slug) and query.
     * Matches full-text (whole words, ranked by ts_rank) OR a substring of the
     * name/description, so partial words work too — e.g. "head" finds
     * "Wireless Headphones". Full-text hits rank above substring-only hits.
     * Empty filters are ignored, so the same query serves the full listing,
     * a category page, and a search result. Native for Postgres tsvector/ts_rank.
     */
    @Query(value = """
            SELECT p.* FROM products p
              LEFT JOIN categories c ON c.id = p.category_id
             WHERE p.is_active = true
               AND (:category = '' OR c.slug = :category)
               AND (:q = ''
                    OR p.search_vector @@ websearch_to_tsquery('english', :q)
                    OR p.name ILIKE '%' || :q || '%'
                    OR p.description ILIKE '%' || :q || '%')
             ORDER BY
               CASE WHEN :q = '' THEN 0
                    ELSE ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) END DESC,
               p.name ASC
            """,
            countQuery = """
            SELECT count(*) FROM products p
              LEFT JOIN categories c ON c.id = p.category_id
             WHERE p.is_active = true
               AND (:category = '' OR c.slug = :category)
               AND (:q = ''
                    OR p.search_vector @@ websearch_to_tsquery('english', :q)
                    OR p.name ILIKE '%' || :q || '%'
                    OR p.description ILIKE '%' || :q || '%')
            """,
            nativeQuery = true)
    Page<Product> search(@Param("category") String category,
                         @Param("q") String q,
                         Pageable pageable);

    /**
     * Autocomplete suggestions: active products whose name contains the term,
     * prefix matches ranked first. Backed by the trigram index.
     */
    @Query(value = """
            SELECT p.id AS id, p.name AS name FROM products p
             WHERE p.is_active = true
               AND p.name ILIKE '%' || :q || '%'
             ORDER BY (CASE WHEN lower(p.name) LIKE lower(:q) || '%' THEN 0 ELSE 1 END), p.name
             LIMIT :limit
            """,
            nativeQuery = true)
    List<ProductSuggestion> autocomplete(@Param("q") String q, @Param("limit") int limit);

    /**
     * Atomically decrements stock only if enough is available. The
     * {@code stock_quantity >= :qty} guard makes the check-and-decrement a
     * single row-locked statement, so concurrent checkouts cannot oversell.
     * Returns the number of rows updated (0 = insufficient stock).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stockQuantity = p.stockQuantity - :qty
             WHERE p.id = :id
               AND p.stockQuantity >= :qty
            """)
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Returns stock to inventory (e.g. when a reservation is released).
     * Note: no clearAutomatically — the caller keeps other managed entities
     * (the orders being cancelled) attached in the same transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
