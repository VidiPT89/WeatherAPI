package com.vidi.weather.entity;

import com.vidi.weather.model.OAuthProvider;
import com.vidi.weather.model.Role;
import com.vidi.weather.model.Units;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** {@code null} for an OAuth-only account that has never set a password. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** {@code null} for a LOCAL (email/password) account. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private OAuthProvider provider;

    /** The provider's stable subject/user id -- only set alongside {@link #provider}. */
    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_units", nullable = false)
    private Units preferredUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    /** Local (email/password) registration. */
    public User(String email, String passwordHash, Units preferredUnits) {
        this(null, email, passwordHash, null, null, preferredUnits, Role.USER, Instant.now());
    }

    /** New account created from a verified OAuth identity -- has no password. */
    public static User oauth(String email, OAuthProvider provider, String providerId, Units preferredUnits) {
        return new User(null, email, null, provider, providerId, preferredUnits, Role.USER, Instant.now());
    }

    private User(
            Long id,
            String email,
            String passwordHash,
            OAuthProvider provider,
            String providerId,
            Units preferredUnits,
            Role role,
            Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerId = providerId;
        this.preferredUnits = preferredUnits;
        this.role = role;
        this.createdAt = createdAt;
    }

    public User withPreferredUnits(Units newPreferredUnits) {
        return new User(id, email, passwordHash, provider, providerId, newPreferredUnits, role, createdAt);
    }

    public User withRole(Role newRole) {
        return new User(id, email, passwordHash, provider, providerId, preferredUnits, newRole, createdAt);
    }

    /** Links an existing LOCAL account to a verified OAuth identity sharing its email. */
    public User withOAuthLink(OAuthProvider newProvider, String newProviderId) {
        return new User(id, email, passwordHash, newProvider, newProviderId, preferredUnits, role, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public Units getPreferredUnits() {
        return preferredUnits;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
