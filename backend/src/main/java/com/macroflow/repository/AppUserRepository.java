package com.macroflow.repository;

import com.macroflow.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AppUser}.
 * Primary lookup is by (provider, providerSub) — the unique pair that identifies
 * a user across sign-in sessions.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Find a user by their auth provider and the subject claim from the provider's ID token. */
    Optional<AppUser> findByProviderAndProviderSub(String provider, String providerSub);
}
