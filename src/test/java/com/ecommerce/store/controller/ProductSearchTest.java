package com.ecommerce.store.controller;

import com.ecommerce.store.dto.response.ProductResponse;
import com.ecommerce.store.model.Category;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.repository.CategoryRepository;
import com.ecommerce.store.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 5 (ported from shopify-plus) — storefront search, category filter,
 * and pagination over the active catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSearchTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;

    private Category newCategory(String base) {
        Category c = new Category();
        c.setName(base + "-name-" + System.nanoTime());
        c.setSlug(base + "-slug-" + System.nanoTime());
        return categories.save(c);
    }

    private Product newProduct(String name, String description, Category category) {
        Product p = new Product();
        p.setSku("SRCH-" + System.nanoTime());
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal("9.99"));
        p.setStockQuantity(5);
        p.setActive(true);
        p.setCategory(category);
        return products.save(p);
    }

    @SuppressWarnings("unchecked")
    private List<ProductResponse> renderedProducts(MvcResult result) {
        return (List<ProductResponse>) result.getModelAndView().getModel().get("products");
    }

    @Test
    void searchMatchesNameCaseInsensitively() throws Exception {
        String token = "zqxytoken" + System.nanoTime();
        newProduct("Special " + token + " Gadget", "irrelevant", null);

        MvcResult result = mockMvc.perform(get("/products").param("q", token.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/products"))
                .andReturn();

        List<ProductResponse> found = renderedProducts(result);
        assertThat(found).isNotEmpty();
        assertThat(found).allMatch(p ->
                p.name().toLowerCase().contains(token)
                        || (p.description() != null && p.description().toLowerCase().contains(token)));
        assertThat(found).anyMatch(p -> p.name().contains(token));
    }

    @Test
    void categoryFilterReturnsOnlyThatCategory() throws Exception {
        Category target = newCategory("filter");
        Category other = newCategory("other");
        newProduct("In target A", null, target);
        newProduct("In target B", null, target);
        newProduct("In other", null, other);

        MvcResult result = mockMvc.perform(get("/products").param("category", target.getSlug()))
                .andExpect(status().isOk())
                .andReturn();

        List<ProductResponse> found = renderedProducts(result);
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(p -> target.getName().equals(p.categoryName()));
    }

    @Test
    void paginationSplitsResultsAcrossPages() throws Exception {
        Category cat = newCategory("paged");
        for (int i = 0; i < 15; i++) {
            newProduct("Paged item " + i, null, cat);
        }

        MvcResult page0 = mockMvc.perform(get("/products")
                        .param("category", cat.getSlug()).param("page", "0"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult page1 = mockMvc.perform(get("/products")
                        .param("category", cat.getSlug()).param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(renderedProducts(page0)).hasSize(12); // PAGE_SIZE
        assertThat(renderedProducts(page1)).hasSize(3);   // remainder
        assertThat(page0.getModelAndView().getModel().get("totalPages")).isEqualTo(2);
    }
}
