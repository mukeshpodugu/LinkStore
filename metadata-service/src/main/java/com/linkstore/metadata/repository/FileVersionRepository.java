package com.linkstore.metadata.repository;

import com.linkstore.metadata.model.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);
    Optional<FileVersion> findByFileIdAndVersionNumber(UUID fileId, Integer versionNumber);
}
