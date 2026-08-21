package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.dto.request.CreateIntentRequest;
import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.OrderStatus;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.repository.OrderRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8 (ported from shopify-plus) — Stripe payments in mock mode:
 * create-intent stores the intent id; confirming settles PENDING -> PAID and
 * releases the stock hold.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;

    private Long productId;

    @BeforeEach
    void seed() {
        Product p = new Product();
        p.setSku("PAY-" + System.nanoTime());
        p.setName("Pay Widget");
        p.setPrice(new BigDecimal("50.00"));
        p.setStockQuantity(10);
        p.setActive(true);
        productId = productRepository.save(p).getId();
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(b -> b.subject("kc-pay").claim("email", "pay@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** Adds to cart, checks out, and returns the created order's id. */
    private Long placePendingOrder() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(productId, 1))))
                .andExpect(status().isOk());

        MvcResult checkout = mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutRequest("1 Main St", "Town", "12345"))))
                .andExpect(status().isCreated())
                .andReturn();

        String orderNumber = objectMapper.readTree(checkout.getResponse().getContentAsString())
                .get("orderNumber").asText();
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow().getId();
    }

    @Test
    void createIntentReturnsClientSecretAndStoresIntentId() throws Exception {
        Long orderId = placePendingOrder();

        mockMvc.perform(post("/api/payments/create-intent").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIntentRequest(orderId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").isNotEmpty());

        assertThat(orderRepository.findById(orderId).orElseThrow().getPaymentIntentId())
                .startsWith("pi_mock_");
    }

    @Test
    void confirmingPaymentSettlesOrderAndReleasesHold() throws Exception {
        Long orderId = placePendingOrder();

        mockMvc.perform(post("/api/payments/confirm").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIntentRequest(orderId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        Order settled = orderRepository.findById(orderId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(settled.getPaidAt()).isNotNull();
        assertThat(settled.getReservationExpiresAt()).isNull(); // hold released
    }
}
