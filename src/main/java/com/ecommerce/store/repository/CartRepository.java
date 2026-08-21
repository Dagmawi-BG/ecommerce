package com.ecommerce.store.repository;

import com.ecommerce.store.model.Cart;
import com.ecommerce.store.model.UserProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** Fetches the cart with its items+products eagerly to avoid N+1 when building a response. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUser(UserProfile user);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByGuestToken(String guestToken);
}
