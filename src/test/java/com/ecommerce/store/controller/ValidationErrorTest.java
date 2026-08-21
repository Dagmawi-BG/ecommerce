package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.request.ProductCreateRequest;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 — validation + RFC 7807. Malformed API payloads return
 * application/problem+json with field errors; unknown web routes render the
 * custom 404 page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ValidationErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt()
                .jwt(builder -> builder.subject("kc-val").claim("email", "val@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void invalidProductPayloadReturnsProblemDetail() throws Exception {
        // blank sku/name, negative price, negative stock -> multiple field violations
        String body = objectMapper.writeValueAsString(new ProductCreateRequest(
                "", "", null, new BigDecimal("-1.00"), -5, null, null));

        mockMvc.perform(post("/api/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void invalidCartQuantityReturnsFieldError() throws Exception {
        // quantity 0 violates @Min(1); validation fires before the service runs
        String body = objectMapper.writeValueAsString(new CartItemRequest(1L, 0));

        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    void unknownProductPageRendersNotFound() throws Exception {
        mockMvc.perform(get("/products/99999999"))
                .andExpect(status().isNotFound());
    }
}
