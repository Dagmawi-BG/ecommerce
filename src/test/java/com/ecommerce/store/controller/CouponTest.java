package com.ecommerce.store.controller;

import com.ecommerce.store.dto.request.ApplyCouponRequest;
import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.dto.request.CouponCreateRequest;
import com.ecommerce.store.model.CouponType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 (ported from shopify-plus) — coupons: percentage/fixed discounts
 * applied to the cart and snapshotted onto the order; invalid codes rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void seed() {
        Product p = new Product();
        p.setSku("COUP-" + System.nanoTime());
        p.setName("Coupon Widget");
        p.setPrice(new BigDecimal("100.00"));
        p.setStockQuantity(10);
        p.setActive(true);
        productId = productRepository.save(p).getId();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(b -> b.subject("kc-coupon").claim("email", "coupon@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void createCoupon(String code, CouponType type, String amount) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CouponCreateRequest(code, type, new BigDecimal(amount), null, true));
        mockMvc.perform(post("/api/coupons").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void addToCart(int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(productId, qty))))
                .andExpect(status().isOk());
    }

    private String applyBody(String code) throws Exception {
        return objectMapper.writeValueAsString(new ApplyCouponRequest(code));
    }

    @Test
    void percentageCouponDiscountsCartTotal() throws Exception {
        createCoupon("SAVE10", CouponType.PERCENTAGE, "10.00");
        addToCart(1); // subtotal 100.00

        mockMvc.perform(post("/api/cart/coupon").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody("save10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(100.00))
                .andExpect(jsonPath("$.discount").value(10.00))
                .andExpect(jsonPath("$.total").value(90.00))
                .andExpect(jsonPath("$.couponCode").value("SAVE10"));
    }

    @Test
    void fixedCouponDiscountsCartTotal() throws Exception {
        createCoupon("MINUS15", CouponType.FIXED, "15.00");
        addToCart(1);

        mockMvc.perform(post("/api/cart/coupon").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody("MINUS15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(15.00))
                .andExpect(jsonPath("$.total").value(85.00));
    }

    @Test
    void unknownCouponIsRejected() throws Exception {
        addToCart(1);
        mockMvc.perform(post("/api/cart/coupon").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody("NOPE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void discountIsSnapshottedOntoOrder() throws Exception {
        createCoupon("SAVE10", CouponType.PERCENTAGE, "10.00");
        addToCart(1);
        mockMvc.perform(post("/api/cart/coupon").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody("SAVE10")))
                .andExpect(status().isOk());

        String checkout = objectMapper.writeValueAsString(
                new CheckoutRequest("1 Main St", "Town", "12345"));
        mockMvc.perform(post("/api/orders").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(checkout))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotalAmount").value(100.00))
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.couponCode").value("SAVE10"))
                .andExpect(jsonPath("$.totalAmount").value(90.00));
    }
}
