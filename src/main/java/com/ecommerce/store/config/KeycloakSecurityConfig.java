package com.ecommerce.store.config;

import com.ecommerce.store.security.GuestCartMergeSuccessHandler;
import com.ecommerce.store.security.JwtAuthConverter;
import com.ecommerce.store.security.KeycloakRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Two ordered filter chains:
 *  (1) /api/**  -> stateless resource server, validates Bearer JWTs.
 *  (2) all else -> browser login via Keycloak (Authorization Code) + OIDC logout.
 *
 * Both derive roles through {@link KeycloakRoles} so authority mapping is identical.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class KeycloakSecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    @Value("${app.post-logout-redirect-uri}")
    private String postLogoutRedirectUri;

    public KeycloakSecurityConfig(JwtAuthConverter jwtAuthConverter) {
        this.jwtAuthConverter = jwtAuthConverter;
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webSecurityChain(HttpSecurity http,
                                         ClientRegistrationRepository clientRegistrationRepository,
                                         GuestCartMergeSuccessHandler guestCartMergeSuccessHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/products/**", "/cart/**",
                                "/css/**", "/js/**", "/images/**", "/webjars/**",
                                "/error", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .successHandler(guestCartMergeSuccessHandler)   // merge guest cart on login
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(userAuthoritiesMapper())))
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)));
        return http.build();
    }

    /**
     * Login-flow equivalent of {@link JwtAuthConverter}: pulls realm roles out of
     * the OIDC id-token so {@code hasRole('ADMIN')} works on Thymeleaf routes too.
     */
    GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidc) {
                    mapped.addAll(KeycloakRoles.fromRealmAccess(
                            oidc.getIdToken().getClaim("realm_access")));
                }
            });
            return mapped;
        };
    }

    LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repo) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(repo);
        handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        return handler;
    }
}
