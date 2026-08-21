package com.ecommerce.store.service;

import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.dto.response.OrderResponse;
import com.ecommerce.store.exception.EmptyCartException;
import com.ecommerce.store.exception.InsufficientStockException;
import com.ecommerce.store.exception.InvalidCheckoutException;
import com.ecommerce.store.model.Cart;
import com.ecommerce.store.model.CartItem;
import com.ecommerce.store.model.Order;
import com.ecommerce.store.model.OrderItem;
import com.ecommerce.store.model.OrderStatus;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.model.ShippingAddress;
import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.OrderRepository;
import com.ecommerce.store.repository.ProductRepository;
import com.ecommerce.store.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final CartService cartService;
    private final UserProfileService userProfiles;
    private final long reservationMinutes;

    public OrderService(OrderRepository orders, ProductRepository products,
                        CartService cartService, UserProfileService userProfiles,
                        @Value("${app.checkout.reservation-minutes:15}") long reservationMinutes) {
        this.orders = orders;
        this.products = products;
        this.cartService = cartService;
        this.userProfiles = userProfiles;
        this.reservationMinutes = reservationMinutes;
    }

    /**
     * Places an order within a single transaction: atomically decrements stock
     * (rolling back everything on shortage), snapshots each line, computes the
     * total, and removes the ordered quantities from the cart.
     *
     * <p>With a partial {@code selection} only the listed products (and the
     * requested quantities) are ordered; unselected lines and any leftover
     * quantity stay in the cart.
     */
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Cart cart = cartService.currentCartEntity();
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Map<Long, Integer> requested = request.hasSelection()
                ? requestedQuantities(request.selection())
                : null;

        // Decide the lines being ordered now (as transient product+qty pairs) and
        // adjust the managed cart for leftovers, without mutating it mid-iteration.
        List<CartItem> selectedLines = new ArrayList<>();
        List<CartItem> fullyConsumed = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int cartQty = cartItem.getQuantity();

            int buyQty;
            if (requested == null) {
                buyQty = cartQty;                       // whole cart
            } else if (!requested.containsKey(product.getId())) {
                continue;                               // not selected -> stays in cart
            } else {
                Integer want = requested.get(product.getId());
                buyQty = (want == null) ? cartQty : want;   // null quantity => whole line
            }

            if (buyQty <= 0) {
                throw new InvalidCheckoutException("Quantity must be positive for " + product.getSku());
            }
            if (buyQty > cartQty) {
                throw new InvalidCheckoutException("Requested more than in cart for " + product.getSku());
            }

            CartItem selected = new CartItem();
            selected.setProduct(product);
            selected.setQuantity(buyQty);
            selectedLines.add(selected);

            if (buyQty == cartQty) {
                fullyConsumed.add(cartItem);
            } else {
                cartItem.setQuantity(cartQty - buyQty);     // keep the leftover
            }
        }
        if (selectedLines.isEmpty()) {
            throw new InvalidCheckoutException("None of the selected items are in the cart");
        }

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency("USD");
        order.setShippingAddress(new ShippingAddress(request.street(), request.city(), request.zip()));

        for (CartItem selected : selectedLines) {
            Product product = selected.getProduct();
            int qty = selected.getQuantity();

            int updated = products.decrementStock(product.getId(), qty);
            if (updated == 0) {
                // Guard failed -> not enough stock. Throwing rolls back the whole order.
                throw new InsufficientStockException(product.getSku(), product.getStockQuantity(), qty);
            }

            OrderItem line = new OrderItem();
            line.setProduct(product);
            line.setProductName(product.getName());   // snapshot
            line.setProductSku(product.getSku());      // snapshot
            line.setQuantity(qty);
            line.setUnitPrice(product.getPrice());     // snapshot
            order.addItem(line);
        }

        // Server-side money over the SELECTED lines + the cart's stored coupon.
        CartService.Totals totals = cartService.computeTotals(selectedLines, cart.getCouponCode());
        order.setSubtotalAmount(totals.subtotal());
        order.setDiscountAmount(totals.discount());
        order.setCouponCode(totals.couponCode());
        order.setTotalAmount(totals.total());

        // Hold the decremented stock until paid; the scheduler restores it if this lapses.
        order.setReservationExpiresAt(Instant.now().plus(reservationMinutes, ChronoUnit.MINUTES));

        Order saved = orders.saveAndFlush(order);   // flush so DB-generated subtotals are read back

        fullyConsumed.forEach(cart::removeItem);        // leftovers were already reduced above
        if (cart.getItems().isEmpty()) {
            cart.setCouponCode(null);                   // coupon is consumed once the cart empties
        }

        return OrderResponse.from(saved);
    }

    /** Collapse a selection into productId -> quantity (null = whole line; duplicates summed). */
    private Map<Long, Integer> requestedQuantities(List<CheckoutRequest.LineSelection> selection) {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (CheckoutRequest.LineSelection line : selection) {
            Long pid = line.productId();
            if (pid == null) {
                continue;
            }
            if (!requested.containsKey(pid)) {
                requested.put(pid, line.quantity());
            } else {
                Integer prev = requested.get(pid);
                Integer q = line.quantity();
                requested.put(pid, (prev == null || q == null) ? null : prev + q);
            }
        }
        return requested;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> history() {
        UserProfile user = currentUser();
        return orders.findByUserOrderByCreatedAtDesc(user).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Releases stock held by PENDING orders whose reservation window lapsed
     * (never paid): returns each line's quantity to inventory and cancels the
     * order. Run periodically by {@code ReservationScheduler}.
     */
    @Transactional
    public int releaseExpiredReservations() {
        List<Order> expired = orders.findByStatusAndReservationExpiresAtBefore(
                OrderStatus.PENDING, Instant.now());
        for (Order order : expired) {
            for (OrderItem item : order.getItems()) {
                products.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(Instant.now());
            order.setReservationExpiresAt(null);
        }
        return expired.size();
    }

    private UserProfile currentUser() {
        String keycloakId = SecurityUtils.currentKeycloakId()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
        String email = SecurityUtils.currentClaim("email").orElse(null);
        return userProfiles.getOrCreate(keycloakId, email);
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + date + "-" + suffix;
    }
}
