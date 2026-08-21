package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 — persistent cart. Verifies the cart is created/linked to the
 * authenticated Keycloak subject, that re-adding a product bumps quantity
 * (uk_cart_product), and that stock limits are enforced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void seedProduct() {
        Product product = new Product();
        product.setSku("CART-SKU-" + System.nanoTime());
        product.setName("Cart Widget");
        product.setPrice(new BigDecimal("5.00"));
        product.setStockQuantity(10);
        product.setActive(true);
        productId = productRepository.save(product).getId();
    }

    /** A stable customer identity (Keycloak sub + email) for the cart to attach to. */
    private static RequestPostProcessor customer() {
        return jwt()
                .jwt(builder -> builder.subject("kc-cust-1").claim("email", "cust@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private String addBody(int quantity) throws Exception {
        return objectMapper.writeValueAsString(new CartItemRequest(productId, quantity));
    }

    @Test
    void addingItemCreatesCartLinkedToUser() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(productId));
    }

    @Test
    void addingSameProductAgainUpdatesQuantityNotDuplicate() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))     // still one row
                .andExpect(jsonPath("$.items[0].quantity").value(5)); // 2 + 3
    }

    @Test
    void addingBeyondAvailableStockIsRejected() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(addBody(999)))
                .andExpect(status().isConflict());
    }

    @Test
    void anonymousCannotAccessCart() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }
}
