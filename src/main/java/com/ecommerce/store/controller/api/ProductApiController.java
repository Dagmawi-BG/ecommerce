package com.ecommerce.store.controller.api;

import com.ecommerce.store.dto.request.ProductCreateRequest;
import com.ecommerce.store.dto.response.ProductResponse;
import com.ecommerce.store.dto.response.ProductSuggestion;
import com.ecommerce.store.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.listActive();
    }

    /** Public autocomplete suggestions for the storefront search box. */
    @GetMapping("/autocomplete")
    public List<ProductSuggestion> autocomplete(@RequestParam String q,
                                                @RequestParam(defaultValue = "8") int limit) {
        return productService.autocomplete(q, limit);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }
}
