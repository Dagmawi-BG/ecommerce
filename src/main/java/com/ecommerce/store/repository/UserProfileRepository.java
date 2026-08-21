package com.ecommerce.store.repository;

import com.ecommerce.store.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByKeycloakId(String keycloakId);
}
