package com.animelist.backend.repository;

import com.animelist.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Byte> {
}
