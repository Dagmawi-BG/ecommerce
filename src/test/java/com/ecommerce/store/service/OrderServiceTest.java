package com.ecommerce.store.service;

import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.exception.InsufficientStockException;
import com.ecommerce.store.model.Cart;
import com.ecommerce.store.model.CartItem;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.CartRepository;
import com.ecommerce.store.repository.OrderRepository;
import com.ecommerce.store.repository.ProductRepository;
import com.ecommerce.store.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 — the ACID guarantee. Two users race to buy the last unit in stock.
 * The atomic {@code decrementStock} guard must let exactly one succeed and must
 * never drive stock negative.
 */
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private OrderRepository orderRepository;

    private Long productId;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> cartIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        Product product = new Product();
        product.setSku("RACE-SKU-" + System.nanoTime());
        product.setName("Last Unit");
        product.setPrice(new BigDecimal("9.99"));
        product.setStockQuantity(1);   // only one available
        product.setActive(true);
        productId = productRepository.save(product).getId();

        cartIds.add(createCartWithOneUnit("kc-race-a", "a@example.com", product));
        cartIds.add(createCartWithOneUnit("kc-race-b", "b@example.com", product));
    }

    private Long createCartWithOneUnit(String sub, String email, Product product) {
        UserProfile user = new UserProfile();
        user.setKeycloakId(sub);
        user.setEmail(email);
        user = userProfileRepository.save(user);
        userIds.add(user.getId());

        Cart cart = new Cart();
        cart.setUser(user);
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(1);
        cart.addItem(item);
        return cartRepository.save(cart).getId();
    }

    @AfterEach
    void cleanup() {
        orderRepository.deleteAll(orderRepository.findByUser_IdIn(userIds));
        cartRepository.deleteAllById(cartIds);
        productRepository.deleteById(productId);
        userProfileRepository.deleteAllById(userIds);
        userIds.clear();
        cartIds.clear();
    }

    @Test
    void twoCheckoutsForTheLastUnit_onlyOneSucceeds() throws Exception {
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Boolean> a = pool.submit(checkoutTask("kc-race-a", "a@example.com", startGun));
        Future<Boolean> b = pool.submit(checkoutTask("kc-race-b", "b@example.com", startGun));

        startGun.countDown();   // release both threads simultaneously
        boolean aSucceeded = a.get();
        boolean bSucceeded = b.get();
        pool.shutdown();

        // Exactly one wins.
        assertThat(aSucceeded ^ bSucceeded).isTrue();
        // Stock is exhausted, never negative.
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isZero();
        // Exactly one order was persisted.
        assertThat(orderRepository.findByUser_IdIn(userIds)).hasSize(1);
    }

    /** Returns true if this user's checkout committed, false if it lost the race. */
    private Callable<Boolean> checkoutTask(String sub, String email, CountDownLatch startGun) {
        return () -> {
            startGun.await();
            authenticate(sub, email);
            try {
                orderService.checkout(new CheckoutRequest("1 Main St", "Town", "00000"));
                return true;
            } catch (InsufficientStockException expectedForLoser) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void authenticate(String sub, String email) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(sub).claim("email", email).build();
        AbstractAuthenticationToken auth =
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
