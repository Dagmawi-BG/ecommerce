package com.ecommerce.store.service;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.response.CartResponse;
import com.ecommerce.store.exception.InsufficientStockException;
import com.ecommerce.store.exception.InvalidCouponException;
import com.ecommerce.store.exception.ResourceNotFoundException;
import com.ecommerce.store.model.Cart;
import com.ecommerce.store.model.CartItem;
import com.ecommerce.store.model.Coupon;
import com.ecommerce.store.model.CouponType;
import com.ecommerce.store.model.Product;
import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.CartRepository;
import com.ecommerce.store.repository.CouponRepository;
import com.ecommerce.store.repository.ProductRepository;
import com.ecommerce.store.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    /** Cookie that carries an anonymous shopper's guest-cart id. */
    public static final String GUEST_COOKIE_NAME = "guestCartId";

    private final CartRepository carts;
    private final ProductRepository products;
    private final CouponRepository coupons;
    private final UserProfileService userProfiles;

    public CartService(CartRepository carts, ProductRepository products,
                       CouponRepository coupons, UserProfileService userProfiles) {
        this.carts = carts;
        this.products = products;
        this.coupons = coupons;
        this.userProfiles = userProfiles;
    }

    @Transactional
    public CartResponse view() {
        return toResponse(currentCart());
    }

    /** Managed cart entity for the current user — used by checkout within the same transaction. */
    @Transactional
    public Cart currentCartEntity() {
        return currentCart();
    }

    @Transactional
    public CartResponse addItem(CartItemRequest request) {
        Cart cart = currentCart();
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        int currentQty = cart.findItemByProductId(product.getId())
                .map(CartItem::getQuantity)
                .orElse(0);
        int desiredQty = currentQty + request.quantity();
        requireStock(product, desiredQty);

        cart.findItemByProductId(product.getId()).ifPresentOrElse(
                existing -> existing.setQuantity(desiredQty),   // upsert: same product -> bump qty
                () -> {
                    CartItem item = new CartItem();
                    item.setProduct(product);
                    item.setQuantity(request.quantity());
                    cart.addItem(item);
                });

        return toResponse(carts.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(Long productId, int quantity) {
        Cart cart = currentCart();
        CartItem item = cart.findItemByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item for product", productId));

        if (quantity <= 0) {
            cart.removeItem(item);
        } else {
            requireStock(item.getProduct(), quantity);
            item.setQuantity(quantity);
        }
        return toResponse(carts.save(cart));
    }

    @Transactional
    public CartResponse removeItem(Long productId) {
        Cart cart = currentCart();
        cart.findItemByProductId(productId).ifPresent(cart::removeItem);
        return toResponse(carts.save(cart));
    }

    @Transactional
    public void clear() {
        Cart cart = currentCart();
        cart.getItems().clear();
        cart.setCouponCode(null);
        carts.save(cart);
    }

    /** Validates the code, stores it on the cart, and returns the discounted totals. */
    @Transactional
    public CartResponse applyCoupon(String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase();
        Coupon coupon = coupons.findByCode(code)
                .orElseThrow(() -> new InvalidCouponException("Coupon not found: " + code));
        if (!coupon.isUsable()) {
            throw new InvalidCouponException("Coupon is expired or inactive: " + code);
        }
        Cart cart = currentCart();
        cart.setCouponCode(coupon.getCode());
        return toResponse(carts.save(cart));
    }

    // ---- Money: the server is the single source of truth for every amount ----

    /** Server-computed totals over the given lines + a live-revalidated coupon. */
    public Totals computeTotals(List<CartItem> items, String couponCode) {
        BigDecimal subtotal = items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String appliedCode = couponCode;

        if (couponCode != null) {
            Coupon coupon = coupons.findByCode(couponCode).filter(Coupon::isUsable).orElse(null);
            if (coupon != null) {
                BigDecimal raw = coupon.getDiscountType() == CouponType.PERCENTAGE
                        ? subtotal.multiply(coupon.getAmount())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        : coupon.getAmount();
                discount = raw.min(subtotal).setScale(2, RoundingMode.HALF_UP); // never exceed subtotal
            } else {
                appliedCode = null; // expired/removed -> silently drop
            }
        }

        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Totals(subtotal, discount, total, appliedCode);
    }

    public CartResponse toResponse(Cart cart) {
        List<CartResponse.Line> lines = cart.getItems().stream()
                .map(item -> {
                    BigDecimal unitPrice = item.getProduct().getPrice();
                    return new CartResponse.Line(
                            item.getProduct().getId(),
                            item.getProduct().getSku(),
                            item.getProduct().getName(),
                            unitPrice,
                            item.getQuantity(),
                            unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
                })
                .toList();

        Totals totals = computeTotals(cart.getItems(), cart.getCouponCode());
        return new CartResponse(lines, totals.subtotal(), totals.discount(),
                totals.total(), totals.couponCode(), lines.size());
    }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal total, String couponCode) {
    }

    private void requireStock(Product product, int desiredQty) {
        if (!product.isActive() || desiredQty > product.getStockQuantity()) {
            throw new InsufficientStockException(product.getSku(), product.getStockQuantity(), desiredQty);
        }
    }

    /**
     * Loads (or lazily creates) the current shopper's cart. Authenticated users
     * get their user-owned cart; anonymous web visitors get a cookie-scoped
     * guest cart (the cookie is minted on first touch).
     */
    private Cart currentCart() {
        Optional<String> keycloakId = SecurityUtils.currentKeycloakId();
        if (keycloakId.isPresent()) {
            String email = SecurityUtils.currentClaim("email").orElse(null);
            UserProfile user = userProfiles.getOrCreate(keycloakId.get(), email);
            return carts.findByUser(user).orElseGet(() -> {
                Cart cart = new Cart();
                cart.setUser(user);
                return carts.save(cart);
            });
        }

        String guestToken = resolveGuestToken();
        return carts.findByGuestToken(guestToken).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setGuestToken(guestToken);
            return carts.save(cart);
        });
    }

    /**
     * On login, fold an anonymous guest cart into the user's cart (summing
     * duplicate lines) and discard the guest cart.
     */
    @Transactional
    public void mergeGuestCart(String guestToken, String keycloakId, String email) {
        if (guestToken == null || guestToken.isBlank()) {
            return;
        }
        Optional<Cart> guestOpt = carts.findByGuestToken(guestToken);
        if (guestOpt.isEmpty()) {
            return;
        }
        Cart guest = guestOpt.get();
        UserProfile user = userProfiles.getOrCreate(keycloakId, email);
        Cart userCart = carts.findByUser(user).orElseGet(() -> {
            Cart c = new Cart();
            c.setUser(user);
            return carts.save(c);
        });

        for (CartItem guestItem : guest.getItems()) {
            userCart.findItemByProductId(guestItem.getProduct().getId()).ifPresentOrElse(
                    existing -> existing.setQuantity(existing.getQuantity() + guestItem.getQuantity()),
                    () -> {
                        CartItem moved = new CartItem();
                        moved.setProduct(guestItem.getProduct());
                        moved.setQuantity(guestItem.getQuantity());
                        userCart.addItem(moved);
                    });
        }
        if (userCart.getCouponCode() == null && guest.getCouponCode() != null) {
            userCart.setCouponCode(guest.getCouponCode());
        }
        carts.save(userCart);
        carts.delete(guest);
    }

    /** Reads the guest cookie from the current request, minting + setting it if absent. */
    private String resolveGuestToken() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No request context for a guest cart");
        }
        HttpServletRequest request = attrs.getRequest();
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (GUEST_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        String token = "guest_" + UUID.randomUUID();
        HttpServletResponse response = attrs.getResponse();
        if (response != null) {
            Cookie cookie = new Cookie(GUEST_COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
            response.addCookie(cookie);
        }
        return token;
    }
}
