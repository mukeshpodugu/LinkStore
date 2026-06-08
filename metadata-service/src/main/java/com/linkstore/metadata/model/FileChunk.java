package com.linkstore.metadata.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_version_id", nullable = false)
    private FileVersion fileVersion;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256; // Checksum for physical file validation

    @Column(name = "primary_node_id", nullable = false, length = 50)
    private String primaryNodeId; // e.g. "storage-node-1"

    @Column(name = "replica_node_ids", nullable = false)
    private String replicaNodeIds; // Comma-separated node names e.g. "storage-node-2,storage-node-3"

    @Column(nullable = false, length = 20)
    private String status; // ACTIVE, REPLICATING, FAILED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}
