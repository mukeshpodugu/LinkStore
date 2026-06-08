package com.linkstore.metadata.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"file_id", "version_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
