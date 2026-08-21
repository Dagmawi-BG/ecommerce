package com.ecommerce.store.controller;

import com.ecommerce.store.dto.response.CartResponse;
import com.ecommerce.store.model.Cart;
import com.ecommerce.store.model.CartItem;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.CartRepository;
import com.ecommerce.store.repository.ProductRepository;
import com.ecommerce.store.repository.UserProfileRepository;
import com.ecommerce.store.service.CartService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 (ported from shopify-plus) — guest carts: an anonymous shopper gets a
 * cookie-scoped cart, which merges into their user cart on login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestCartTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CartRepository carts;
    @Autowired
    private UserProfileRepository userProfiles;
    @Autowired
    private CartService cartService;

    private Long productId;

    @BeforeEach
    void seed() {
        Product p = new Product();
        p.setSku("GUEST-" + System.nanoTime());
        p.setName("Guest Widget");
        p.setPrice(new BigDecimal("12.00"));
        p.setStockQuantity(10);
        p.setActive(true);
        productId = products.save(p).getId();
    }

    @Test
    void anonymousShopperGetsCookieScopedCart() throws Exception {
        // Guest adds an item — no auth. A guest cookie is minted on the response.
        MvcResult add = mockMvc.perform(post("/cart/items").with(csrf())
                        .param("productId", productId.toString())
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie guestCookie = add.getResponse().getCookie(CartService.GUEST_COOKIE_NAME);
        assertThat(guestCookie).isNotNull();
        assertThat(guestCookie.getValue()).startsWith("guest_");

        // Sending that cookie back returns the same guest cart.
        MvcResult view = mockMvc.perform(get("/cart").cookie(guestCookie))
                .andExpect(status().isOk())
                .andReturn();

        CartResponse cart = (CartResponse) view.getModelAndView().getModel().get("cart");
        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void guestCartMergesIntoUserCartOnLogin() throws Exception {
        String token = "guest_merge_" + System.nanoTime();
        Product product = products.findById(productId).orElseThrow();

        Cart guest = new Cart();
        guest.setGuestToken(token);
        CartItem gi = new CartItem();
        gi.setProduct(product);
        gi.setQuantity(3);
        guest.addItem(gi);
        carts.save(guest);

        String keycloakId = "kc-merge-" + System.nanoTime();
        UserProfile user = new UserProfile();
        user.setKeycloakId(keycloakId);
        user.setEmail("merge-" + System.nanoTime() + "@example.com");
        userProfiles.save(user);

        cartService.mergeGuestCart(token, keycloakId, user.getEmail());

        Cart userCart = carts.findByUser(user).orElseThrow();
        assertThat(userCart.getItems()).hasSize(1);
        assertThat(userCart.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(carts.findByGuestToken(token)).isEmpty(); // guest cart consumed
    }
}
