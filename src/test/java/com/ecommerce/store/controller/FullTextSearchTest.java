package com.ecommerce.store.controller;

import com.ecommerce.store.dto.response.ProductResponse;
import com.ecommerce.store.model.Product;
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
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Postgres full-text search (ranked, word-aware) + autocomplete suggestions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FullTextSearchTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository products;

    private Product newProduct(String name, String description) {
        Product p = new Product();
        p.setSku("FTS-" + System.nanoTime());
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal("15.00"));
        p.setStockQuantity(5);
        p.setActive(true);
        return products.save(p);
    }

    @SuppressWarnings("unchecked")
    private List<ProductResponse> rendered(MvcResult result) {
        return (List<ProductResponse>) result.getModelAndView().getModel().get("products");
    }

    @Test
    void fullTextSearchMatchesAWordInTheDescription() throws Exception {
        Product target = newProduct("Gizmo", "a quokkagadget powered device");
        newProduct("Unrelated", "nothing to see here");

        MvcResult result = mockMvc.perform(get("/products").param("q", "quokkagadget"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(rendered(result))
                .anyMatch(p -> p.sku().equals(target.getSku()))
                .allMatch(p -> p.name().equals("Gizmo"));
    }

    @Test
    void partialWordFindsRelatedProducts() throws Exception {
        // "head" must return "Wireless Headphones" even though it's only a word fragment.
        Product target = newProduct("Wireless Headphones", "over-ear bluetooth audio");
        newProduct("Desk Lamp", "adjustable lighting");

        MvcResult result = mockMvc.perform(get("/products").param("q", "head"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(rendered(result)).anyMatch(p -> p.sku().equals(target.getSku()));
        assertThat(rendered(result)).noneMatch(p -> p.name().equals("Desk Lamp"));
    }

    @Test
    void autocompleteReturnsMatchingSuggestions() throws Exception {
        String token = "Zulqxx" + System.nanoTime();
        newProduct(token + " Keyboard", null);
        newProduct(token + " Mouse", null);
        newProduct("Something else", null);

        mockMvc.perform(get("/api/products/autocomplete").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name", containsStringIgnoringCase(token)))
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void shortAutocompleteQueryReturnsNothingExtra() throws Exception {
        // Service ignores blank queries; a single space yields an empty list.
        mockMvc.perform(get("/api/products/autocomplete").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
