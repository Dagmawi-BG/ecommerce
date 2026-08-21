package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.ProductCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 1 — product catalog: public reads (web + API) and admin-only creation.
 * Rolls back after each test; authenticates via injected JWT authorities so the
 * suite needs Postgres only, not a live Keycloak token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void webProductsPageRendersWithModel() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/products"))
                .andExpect(model().attributeExists("products"));
    }

    @Test
    void apiProductListReturnsJson() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        String body = objectMapper.writeValueAsString(new ProductCreateRequest(
                "SKU-CREATE-1", "Widget", "A useful widget",
                new BigDecimal("9.99"), 10, null, null));

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-CREATE-1"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void nonAdminCannotCreateProduct() throws Exception {
        String body = objectMapper.writeValueAsString(new ProductCreateRequest(
                "SKU-DENY-1", "Widget", null,
                new BigDecimal("1.00"), 1, null, null));

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        String body = objectMapper.writeValueAsString(new ProductCreateRequest(
                "SKU-ANON-1", "Widget", null,
                new BigDecimal("1.00"), 1, null, null));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
