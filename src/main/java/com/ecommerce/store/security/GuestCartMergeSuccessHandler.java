package com.ecommerce.store.security;

import com.ecommerce.store.service.CartService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * On successful Keycloak login, folds any anonymous guest cart (identified by
 * the guest cookie) into the now-authenticated user's cart, then clears the
 * cookie and proceeds with the normal post-login redirect.
 */
@Component
public class GuestCartMergeSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final CartService cartService;

    public GuestCartMergeSuccessHandler(CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OidcUser oidc) {
            String guestToken = readGuestCookie(request);
            if (guestToken != null) {
                cartService.mergeGuestCart(guestToken, oidc.getSubject(), oidc.getEmail());
                Cookie cleared = new Cookie(CartService.GUEST_COOKIE_NAME, "");
                cleared.setPath("/");
                cleared.setMaxAge(0);   // expire it — the cart now lives under the user
                response.addCookie(cleared);
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private String readGuestCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (CartService.GUEST_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
