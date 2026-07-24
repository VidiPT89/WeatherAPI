package com.vidi.weather.service;

import com.vidi.weather.dto.FavoriteResponse;
import com.vidi.weather.entity.Favorite;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.FavoriteAlreadyExistsException;
import com.vidi.weather.repository.FavoriteRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
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
        // The exists-check above and this save aren't atomic -- two concurrent adds of the same
        // city can both pass the check. The database's case-insensitive unique index is the real
        // backstop; translate its violation into the same friendly exception the check above
        // throws, instead of letting a raw DataIntegrityViolationException reach the client.
        try {
            Favorite favorite = favoriteRepository.save(new Favorite(user, city));
            return toResponse(favorite);
        } catch (DataIntegrityViolationException alreadyExists) {
            throw new FavoriteAlreadyExistsException(city);
        }
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
