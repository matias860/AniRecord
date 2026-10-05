package com.animelist.backend.repository;

import com.animelist.backend.entity.UserProfileAsset;
import com.animelist.backend.enums.ProfileAssetType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserProfileAssetRepository extends JpaRepository<UserProfileAsset, Long> {
    Optional<UserProfileAsset> findByUserIdAndAssetType(Long userId, ProfileAssetType assetType);
    boolean existsByUserIdAndAssetType(Long userId, ProfileAssetType assetType);
    void deleteByUserIdAndAssetType(Long userId, ProfileAssetType assetType);
    @Query("select a.updatedAt from UserProfileAsset a where a.user.id = :userId and a.assetType = :type")
    Optional<java.time.LocalDateTime> findUpdatedAt(@Param("userId") Long userId, @Param("type") ProfileAssetType type);
    @Query("select a.user.id, a.updatedAt from UserProfileAsset a where a.user.id in :userIds and a.assetType = :type")
    List<Object[]> findUpdatedAtByUserIds(@Param("userIds") List<Long> userIds, @Param("type") ProfileAssetType type);
}
