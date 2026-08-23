package com.animelist.backend.repository;

import com.animelist.backend.entity.MediaRelationSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRelationSnapshotRepository extends JpaRepository<MediaRelationSnapshot, Long> {
    List<MediaRelationSnapshot> findBySourceMediaId(Long sourceMediaId);
}
