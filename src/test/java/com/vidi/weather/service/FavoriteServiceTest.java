package com.vidi.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vidi.weather.dto.FavoriteResponse;
import com.vidi.weather.entity.Favorite;
import com.vidi.weather.entity.User;
import com.vidi.weather.exception.FavoriteAlreadyExistsException;
import com.vidi.weather.model.Units;
import com.vidi.weather.repository.FavoriteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    private final User user = new User("test@example.com", "hash", Units.METRIC);

    @Test
    void addsFavoriteWhenNotAlreadyPresent() {
        when(favoriteRepository.existsByUserAndCityIgnoreCase(user, "Lisboa")).thenReturn(false);
        when(favoriteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FavoriteResponse response = favoriteService.add(user, "Lisboa");

        assertThat(response.city()).isEqualTo("Lisboa");
    }

    @Test
    void throwsWhenCityAlreadyFavorited() {
        when(favoriteRepository.existsByUserAndCityIgnoreCase(user, "Lisboa")).thenReturn(true);

        assertThatThrownBy(() -> favoriteService.add(user, "Lisboa"))
                .isInstanceOf(FavoriteAlreadyExistsException.class);
    }

    @Test
    void listsFavoritesForUser() {
        when(favoriteRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(new Favorite(user, "Porto")));

        List<FavoriteResponse> result = favoriteService.listForUser(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo("Porto");
    }
}
