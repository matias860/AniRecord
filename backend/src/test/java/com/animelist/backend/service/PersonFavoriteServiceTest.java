package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.PersonFavorite;
import com.animelist.backend.enums.PersonType;
import com.animelist.backend.repository.PersonFavoriteRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonFavoriteServiceTest {
    @Test
    void importedPositionsComeFirstAndLocalFavoritesKeepTheirRelativeOrder() {
        var repository = mock(PersonFavoriteRepository.class);
        var current = mock(CurrentUserService.class);
        var user = new AppUser(); user.setId(1L);
        when(current.require()).thenReturn(user);
        var older = favorite(1, null, 1);
        var newer = favorite(2, null, 2);
        var second = favorite(3, 1, 3);
        var first = favorite(4, 0, 4);
        when(repository.findAllByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(first, second, newer, older));
        var service = new PersonFavoriteService(repository, current);
        assertEquals(List.of(4, 3, 2, 1), service.list().stream().map(item -> item.anilistId()).toList());
    }
    private static PersonFavorite favorite(int id, Integer order, int day) {
        var item = new PersonFavorite(); item.setId((long) id); item.setAnilistId(id);
        item.setName("Character " + id); item.setPersonType(PersonType.CHARACTER);
        item.setAnilistFavoriteOrder(order); item.setCreatedAt(LocalDateTime.of(2026, 10, day, 0, 0));
        return item;
    }
}
