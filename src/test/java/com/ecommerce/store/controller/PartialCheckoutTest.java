package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partial-line checkout (ported from shopify-plus): order only selected
 * quantities; leftovers and unselected lines stay in the cart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartialCheckoutTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;

    private static RequestPostProcessor customer() {
        return jwt().jwt(b -> b.subject("kc-partial").claim("email", "partial@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long seedProduct(String name, int stock) {
        Product p = new Product();
        p.setSku("PART-" + System.nanoTime());
        p.setName(name);
        p.setPrice(new BigDecimal("10.00"));
        p.setStockQuantity(stock);
        p.setActive(true);
        return productRepository.save(p).getId();
    }

    private void addToCart(Long productId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(productId, qty))))
                .andExpect(status().isOk());
    }

    private String checkoutJson(CheckoutRequest.LineSelection... selection) throws Exception {
        return objectMapper.writeValueAsString(
                new CheckoutRequest("1 Main St", "Town", "12345", List.of(selection)));
    }

    @Test
    void partialQuantityOrdersSomeAndLeavesTheRest() throws Exception {
        Long productId = seedProduct("Partial Widget", 20);
        addToCart(productId, 5);

        // Order only 2 of the 5.
        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(new CheckoutRequest.LineSelection(productId, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(20.00));

        // 3 remain in the cart; stock dropped by only 2.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/cart").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(18);
    }

    @Test
    void unselectedLinesStayInCart() throws Exception {
        Long a = seedProduct("Product A", 10);
        Long b = seedProduct("Product B", 10);
        addToCart(a, 2);
        addToCart(b, 3);

        // Order all of A only (null quantity = whole line); B untouched.
        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(new CheckoutRequest.LineSelection(a, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productSku").exists());

        assertThat(productRepository.findById(a).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(productRepository.findById(b).orElseThrow().getStockQuantity()).isEqualTo(10); // untouched
    }

    @Test
    void requestingMoreThanInCartIsRejected() throws Exception {
        Long productId = seedProduct("Scarce Widget", 100);
        addToCart(productId, 2);

        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(new CheckoutRequest.LineSelection(productId, 5))))
                .andExpect(status().isBadRequest());
    }
}
