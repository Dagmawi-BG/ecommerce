package com.ecommerce.store.dto.response;

import com.ecommerce.store.model.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        boolean active,
        String categoryName,
        String imageUrl) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStockQuantity(),
                p.isActive(),
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getImageUrl());
    }
}
