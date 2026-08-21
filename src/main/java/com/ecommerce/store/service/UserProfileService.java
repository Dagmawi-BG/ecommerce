package com.ecommerce.store.service;

import com.ecommerce.store.model.UserProfile;
import com.ecommerce.store.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves the local profile for a Keycloak subject, lazily provisioning it
     * on first authenticated interaction. Email falls back to a stable, unique
     * placeholder derived from the subject when the token omits it.
     */
    @Transactional
    public UserProfile getOrCreate(String keycloakId, String email) {
        return repository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setKeycloakId(keycloakId);
                    profile.setEmail(email != null && !email.isBlank()
                            ? email
                            : keycloakId + "@placeholder.local");
                    return repository.save(profile);
                });
    }
}
