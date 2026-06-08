package com.linkstore.metadata.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shared_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String token; // Unique random token for URL share

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private FileMetadata file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType; // PUBLIC, PRIVATE, PASSWORD_PROTECTED

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (downloadCount == null) downloadCount = 0;
        if (linkType == null) linkType = "PUBLIC";
    }
}
