package com.animelist.backend.repository;

import com.animelist.backend.entity.Media;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByAnilistId(Integer anilistId);
    List<Media> findAllByAnilistIdIn(Collection<Integer> anilistIds);
}
