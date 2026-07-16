package com.vidi.weather.repository;

import com.vidi.weather.entity.Favorite;
import com.vidi.weather.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserOrderByCreatedAtDesc(User user);

    boolean existsByUserAndCityIgnoreCase(User user, String city);
}
