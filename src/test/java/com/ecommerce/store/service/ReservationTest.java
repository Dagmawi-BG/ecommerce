package com.ecommerce.store.service;

import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.OrderItem;
import com.ecommerce.store.model.OrderStatus;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.model.ShippingAddress;
import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.OrderRepository;
import com.ecommerce.store.repository.ProductRepository;
import com.ecommerce.store.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 (ported from shopify-plus) — the reservation scheduler's core:
 * a pending order whose hold lapsed must return its stock and be cancelled.
 */
@SpringBootTest
class ReservationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private UserProfileRepository userProfiles;

    private Long productId;
    private Long orderId;
    private Long userId;

    @AfterEach
    void cleanup() {
        if (orderId != null) orders.deleteById(orderId);
        if (productId != null) products.deleteById(productId);
        if (userId != null) userProfiles.deleteById(userId);
    }

    @Test
    void lapsedReservationRestoresStockAndCancelsOrder() {
        // Product with 2 units already "reserved": 10 total, 8 currently on hand.
        Product product = new Product();
        product.setSku("RES-" + System.nanoTime());
        product.setName("Reserved Widget");
        product.setPrice(new BigDecimal("20.00"));
        product.setStockQuantity(8);
        product.setActive(true);
        productId = products.save(product).getId();

        UserProfile user = new UserProfile();
        user.setKeycloakId("kc-res-" + System.nanoTime());
        user.setEmail("res-" + System.nanoTime() + "@example.com");
        userId = userProfiles.save(user).getId();

        Order order = new Order();
        order.setOrderNumber("ORD-RES-" + System.nanoTime());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency("USD");
        order.setSubtotalAmount(new BigDecimal("40.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("40.00"));
        order.setShippingAddress(new ShippingAddress("1 Main St", "Town", "00000"));
        order.setReservationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)); // already lapsed

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setQuantity(2);
        item.setUnitPrice(product.getPrice());
        order.addItem(item);
        orderId = orders.save(order).getId();

        int released = orderService.releaseExpiredReservations();

        assertThat(released).isEqualTo(1);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(10); // 8 + 2
        Order reloaded = orders.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reloaded.getReservationExpiresAt()).isNull();
    }
}
