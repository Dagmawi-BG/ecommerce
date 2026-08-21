package com.ecommerce.store.controller.web;

import com.ecommerce.store.dto.response.ProductResponse;
import com.ecommerce.store.repository.CategoryRepository;
import com.ecommerce.store.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/products")
public class WebProductController {

    private static final int PAGE_SIZE = 12;

    private final ProductService productService;
    private final CategoryRepository categories;

    public WebProductController(ProductService productService, CategoryRepository categories) {
        this.productService = productService;
        this.categories = categories;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<ProductResponse> result = productService.search(
                category, q, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        model.addAttribute("products", result.getContent());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalItems", result.getTotalElements());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("categories", categories.findAll());
        return "pages/products";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getById(id));
        return "pages/product-detail";
    }
}
