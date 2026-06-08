package com.linkstore.metadata.repository;

import com.linkstore.metadata.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByOwnerIdAndParentId(UUID ownerId, UUID parentId);
    List<Folder> findByOwnerIdAndParentIsNull(UUID ownerId);
    Optional<Folder> findByNameAndParentIdAndOwnerId(String name, UUID parentId, UUID ownerId);
    Optional<Folder> findByNameAndParentIsNullAndOwnerId(String name, UUID ownerId);
}
