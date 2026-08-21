package com.ecommerce.store.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Resource-server converter: maps a Keycloak access-token JWT to an
 * authenticated principal carrying {@code ROLE_*} authorities.
 * Applied to the {@code /api/**} security chain.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities =
                KeycloakRoles.fromRealmAccess(jwt.getClaim("realm_access"));
        String principalName = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }
}
