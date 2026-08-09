package com.animelist.backend.repository;

import com.animelist.backend.entity.PersonFavorite;
import com.animelist.backend.enums.PersonType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonFavoriteRepository extends JpaRepository<PersonFavorite, Long> {
    List<PersonFavorite> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<PersonFavorite> findByUserIdAndAnilistIdAndPersonType(Long userId, Integer anilistId, PersonType personType);
}
