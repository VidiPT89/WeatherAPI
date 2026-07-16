package com.vidi.weather.entity;

import com.vidi.weather.model.Units;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "search_history")
public class SearchHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Units units;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;

    protected SearchHistoryEntry() {
    }

    public SearchHistoryEntry(User user, String city, Units units) {
        this.user = user;
        this.city = city;
        this.units = units;
        this.searchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCity() {
        return city;
    }

    public Units getUnits() {
        return units;
    }

    public Instant getSearchedAt() {
        return searchedAt;
    }
}
