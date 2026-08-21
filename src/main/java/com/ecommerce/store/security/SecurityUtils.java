package com.ecommerce.store.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Principal helpers that work across BOTH authentication styles:
 *  - {@link Jwt}      on /api/** (resource server)
 *  - {@link OidcUser} on web routes (login flow)
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** The Keycloak subject ('sub' claim) of the current caller, if authenticated. */
    public static Optional<String> currentKeycloakId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            return Optional.ofNullable(oidc.getSubject());
        }
        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    /** Reads an arbitrary claim (e.g. email, given_name) from either principal type. */
    public static Optional<String> currentClaim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            return Optional.ofNullable(oidc.getClaims().get(name)).map(Object::toString);
        }
        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsString(name));
        }
        return Optional.empty();
    }
}
