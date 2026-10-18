package com.animelist.backend.repository;

import com.animelist.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    java.util.Optional<AppUser> findByAnilistUserId(Integer anilistUserId);
    java.util.Optional<AppUser> findByLocalUsernameIgnoreCase(String localUsername);
    boolean existsByLocalUsernameIgnoreCase(String localUsername);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    java.util.Optional<AppUser> findForAssistantQuotaById(@Param("id") Long id);
}
