package com.vidi.weather.service;

import com.vidi.weather.dto.FavoriteResponse;
import com.vidi.weather.entity.Favorite;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.FavoriteAlreadyExistsException;
import com.vidi.weather.repository.FavoriteRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public FavoriteService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    public FavoriteResponse add(User user, String city) {
        if (favoriteRepository.existsByUserAndCityIgnoreCase(user, city)) {
            throw new FavoriteAlreadyExistsException(city);
        }
        Favorite favorite = favoriteRepository.save(new Favorite(user, city));
        return toResponse(favorite);
    }

    public List<FavoriteResponse> listForUser(User user) {
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        return new FavoriteResponse(favorite.getCity(), favorite.getCreatedAt());
    }
}
