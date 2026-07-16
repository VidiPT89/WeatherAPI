package com.vidi.weather.entity;

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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_units", nullable = false)
    private Units preferredUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, Units preferredUnits) {
        this(null, email, passwordHash, preferredUnits, Instant.now());
    }

    private User(Long id, String email, String passwordHash, Units preferredUnits, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.preferredUnits = preferredUnits;
        this.createdAt = createdAt;
    }

    public User withPreferredUnits(Units newPreferredUnits) {
        return new User(id, email, passwordHash, newPreferredUnits, createdAt);
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

    public Units getPreferredUnits() {
        return preferredUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
