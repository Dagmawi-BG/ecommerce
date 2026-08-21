package com.ecommerce.store.repository;

import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.OrderStatus;
import com.ecommerce.store.model.UserProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByUserOrderByCreatedAtDesc(UserProfile user);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    List<Order> findByUser_IdIn(Collection<Long> userIds);

    /** Pending orders whose stock reservation has lapsed — candidates for release. */
    @EntityGraph(attributePaths = "items")
    List<Order> findByStatusAndReservationExpiresAtBefore(OrderStatus status, Instant cutoff);
}
