package com.linkstore.metadata.repository;

import com.linkstore.metadata.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    List<FileMetadata> findByOwnerIdAndFolderIdAndIsDeletedFalse(UUID ownerId, UUID folderId);
    List<FileMetadata> findByOwnerIdAndFolderIsNullAndIsDeletedFalse(UUID ownerId);
    Optional<FileMetadata> findByHashSha256(String hashSha256);
    List<FileMetadata> findByOwnerIdAndIsDeletedFalse(UUID ownerId);

    @Query("SELECT f FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.isDeleted = false " +
           "AND (:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:mimeType IS NULL OR f.mimeType = :mimeType) " +
           "AND (:startDate IS NULL OR f.createdAt >= :startDate)")
    List<FileMetadata> searchFiles(
        @Param("ownerId") UUID ownerId,
        @Param("name") String name,
        @Param("mimeType") String mimeType,
        @Param("startDate") LocalDateTime startDate
    );
}
