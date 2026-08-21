package com.ecommerce.store.dto.response;

/** Lightweight autocomplete row (Spring Data projection over the native query). */
public interface ProductSuggestion {

    Long getId();

    String getName();
}
