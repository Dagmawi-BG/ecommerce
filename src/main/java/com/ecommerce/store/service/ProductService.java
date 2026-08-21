package com.ecommerce.store.service;

import com.ecommerce.store.dto.request.ProductCreateRequest;
import com.ecommerce.store.dto.response.ProductResponse;
import com.ecommerce.store.dto.response.ProductSuggestion;
import com.ecommerce.store.exception.ResourceNotFoundException;
import com.ecommerce.store.model.Category;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.repository.CategoryRepository;
import com.ecommerce.store.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActive() {
        return products.findByActiveTrue().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Paged catalog with optional category (slug) and free-text search.
     * Blank filters are normalised to null so they are ignored by the query.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String category, String query, Pageable pageable) {
        // Normalise blanks to "" (not null): a null bind parameter has no inferable
        // SQL type in Postgres; "" is an explicit no-op filter.
        String cat = (category == null) ? "" : category.strip();
        String q = (query == null) ? "" : query.strip();
        return products.search(cat, q, pageable).map(ProductResponse::from);
    }

    /** Autocomplete suggestions for the storefront search box. */
    @Transactional(readOnly = true)
    public List<ProductSuggestion> autocomplete(String query, int limit) {
        String q = (query == null) ? "" : query.strip();
        if (q.isBlank()) {
            return List.of();
        }
        int capped = Math.min(Math.max(limit, 1), 20);
        return products.autocomplete(q, capped);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return products.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Category category = null;
        if (request.categoryId() != null) {
            category = categories.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        }

        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setActive(true);
        product.setCategory(category);
        product.setImageUrl(request.imageUrl());

        return ProductResponse.from(products.save(product));
    }
}
