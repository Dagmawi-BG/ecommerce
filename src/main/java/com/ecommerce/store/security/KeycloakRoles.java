package com.ecommerce.store.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for turning Keycloak's {@code realm_access.roles}
 * claim into Spring {@code ROLE_*} authorities.
 * <p>
 * Used by BOTH security chains so they never drift:
 *  - {@link JwtAuthConverter}          -> /api/** (resource server, access-token JWT)
 *  - the userAuthoritiesMapper bean     -> web login (OIDC id-token)
 */
public final class KeycloakRoles {

    private KeycloakRoles() {
    }

    @SuppressWarnings("unchecked")
    public static Collection<GrantedAuthority> fromRealmAccess(Map<String, Object> realmAccess) {
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(Object::toString)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
