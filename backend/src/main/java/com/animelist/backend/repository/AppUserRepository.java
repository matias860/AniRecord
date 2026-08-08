package com.animelist.backend.repository;

import com.animelist.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    java.util.Optional<AppUser> findByAnilistUserId(Integer anilistUserId);
    java.util.Optional<AppUser> findByLocalUsernameIgnoreCase(String localUsername);
    boolean existsByLocalUsernameIgnoreCase(String localUsername);
}
