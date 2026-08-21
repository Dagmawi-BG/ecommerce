package com.ecommerce.store.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 — proves the two security chains are wired correctly, before any
 * domain controllers exist.
 *
 *  - Public catalog reads pass authorization (reach the dispatcher -> 404 for now).
 *  - Writes to the API without a token are rejected by the resource server (401).
 */
@SpringBootTest
@AutoConfigureMockMvc
class KeycloakSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Fails if the security beans, Flyway migration, or JPA validation break.
    }

    @Test
    void publicProductReadsAreNotBlockedBySecurity() throws Exception {
        // GET /api/products is permitAll: anonymous callers reach the controller -> 200 (NOT 401/403).
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousApiWritesAreRejected() throws Exception {
        // POST /api/products requires authentication -> resource server returns 401.
        mockMvc.perform(post("/api/products"))
                .andExpect(status().isUnauthorized());
    }
}
