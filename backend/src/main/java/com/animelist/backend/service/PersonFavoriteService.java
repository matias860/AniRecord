package com.animelist.backend.service;

import com.animelist.backend.dto.favorite.FavoritePersonRequest;
import com.animelist.backend.dto.favorite.FavoritePersonResponse;
import com.animelist.backend.entity.PersonFavorite;
import com.animelist.backend.repository.PersonFavoriteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonFavoriteService {
    private final PersonFavoriteRepository repository;
    private final CurrentUserService currentUser;
    public PersonFavoriteService(PersonFavoriteRepository repository, CurrentUserService currentUser) { this.repository = repository; this.currentUser = currentUser; }

    @Transactional(readOnly = true)
    public List<FavoritePersonResponse> list() {
        return repository.findAllByUserIdOrderByCreatedAtDesc(currentUser.require().getId()).stream().map(this::response).toList();
    }

    @Transactional
    public FavoritePersonResponse add(FavoritePersonRequest request) {
        var user = currentUser.require();
        return repository.findByUserIdAndAnilistIdAndPersonType(user.getId(), request.anilistId(), request.personType())
                .map(this::response).orElseGet(() -> {
                    PersonFavorite favorite = new PersonFavorite(); favorite.setUser(user); favorite.setAnilistId(request.anilistId());
                    favorite.setPersonType(request.personType()); favorite.setName(request.name().trim()); favorite.setNativeName(normalize(request.nativeName())); favorite.setImageUrl(normalize(request.imageUrl()));
                    return response(repository.save(favorite));
                });
    }

    @Transactional
    public void remove(Integer anilistId, com.animelist.backend.enums.PersonType type) {
        var user = currentUser.require();
        repository.findByUserIdAndAnilistIdAndPersonType(user.getId(), anilistId, type).ifPresent(repository::delete);
    }

    private FavoritePersonResponse response(PersonFavorite item) { return new FavoritePersonResponse(item.getId(), item.getAnilistId(), item.getPersonType(), item.getName(), item.getNativeName(), item.getImageUrl()); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
