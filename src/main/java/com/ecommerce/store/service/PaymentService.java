package com.ecommerce.store.service;

import com.ecommerce.store.dto.response.OrderResponse;
import com.ecommerce.store.exception.ResourceNotFoundException;
import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.OrderStatus;
import com.ecommerce.store.repository.OrderRepository;
import com.ecommerce.store.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Payment orchestration (ported from shopify-plus). Creating an intent stores
 * its id on the order; a successful payment (Stripe webhook in real mode, or the
 * mock confirm in dev) settles the order: PENDING -> PAID, releasing the hold.
 */
@Service
public class PaymentService {

    private final OrderRepository orders;
    private final StripeService stripe;

    public PaymentService(OrderRepository orders, StripeService stripe) {
        this.orders = orders;
        this.stripe = stripe;
    }

    /** Creates a payment intent for the current user's order; returns the client secret. */
    @Transactional
    public String createIntent(Long orderId) {
        Order order = requireOwnOrder(orderId);
        long amountCents = order.getTotalAmount().movePointRight(2).longValueExact();
        StripeService.Intent intent = stripe.createPaymentIntent(
                amountCents, Map.of("orderId", String.valueOf(order.getId())));
        order.setPaymentIntentId(intent.id());
        return intent.clientSecret();
    }

    /** Real-mode settlement: called from the verified Stripe webhook. */
    @Transactional
    public void markPaidByIntent(String paymentIntentId) {
        orders.findByPaymentIntentId(paymentIntentId).ifPresent(this::settle);
    }

    /** Mock/dev settlement: the current user confirms their own order's payment. */
    @Transactional
    public OrderResponse confirmMockPayment(Long orderId) {
        Order order = requireOwnOrder(orderId);
        settle(order);
        return OrderResponse.from(order);
    }

    /** Mock/dev settlement by order number — used by the web "Pay now" button. */
    @Transactional
    public void confirmMockPaymentByNumber(String orderNumber) {
        String keycloakId = currentKeycloakId();
        Order order = orders.findByOrderNumber(orderNumber)
                .filter(o -> o.getUser().getKeycloakId().equals(keycloakId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        settle(order);
    }

    private void settle(Order order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
            order.setReservationExpiresAt(null);   // paid -> stock is no longer just "held"
        }
    }

    private Order requireOwnOrder(Long orderId) {
        String keycloakId = currentKeycloakId();
        return orders.findById(orderId)
                .filter(o -> o.getUser().getKeycloakId().equals(keycloakId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private String currentKeycloakId() {
        return SecurityUtils.currentKeycloakId()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
