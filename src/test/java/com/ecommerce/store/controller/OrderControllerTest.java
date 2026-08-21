package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.request.CheckoutRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 — checkout via the API: happy path reduces stock and snapshots lines,
 * insufficient stock is rejected, and anonymous checkout is blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

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
        product.setSku("ORDER-SKU-" + System.nanoTime());
        product.setName("Order Widget");
        product.setPrice(new BigDecimal("4.00"));
        product.setStockQuantity(5);
        product.setActive(true);
        productId = productRepository.save(product).getId();
    }

    private static RequestPostProcessor customer() {
        return jwt()
                .jwt(builder -> builder.subject("kc-order-cust").claim("email", "buyer@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void addToCart(int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(productId, quantity))))
                .andExpect(status().isOk());
    }

    private String checkoutBody() throws Exception {
        return objectMapper.writeValueAsString(new CheckoutRequest("1 Main St", "Springfield", "12345"));
    }

    @Test
    void checkoutCreatesOrderReducesStockAndSnapshotsLines() throws Exception {
        addToCart(2);

        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(8.00))
                .andExpect(jsonPath("$.items[0].productName").value("Order Widget"))
                .andExpect(jsonPath("$.items[0].subtotal").value(8.00)); // DB-generated column

        // 5 - 2 = 3 (repo read is fresh: decrementStock clears the persistence context)
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test
    void checkoutRejectedWhenStockInsufficient() throws Exception {
        addToCart(1);

        // Drain stock behind the cart so the atomic guard fails at checkout.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStockQuantity(0);
        productRepository.saveAndFlush(product);

        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void anonymousCannotCheckout() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody()))
                .andExpect(status().isUnauthorized());
    }
}
