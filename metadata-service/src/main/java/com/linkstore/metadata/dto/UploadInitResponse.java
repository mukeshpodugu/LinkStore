package com.linkstore.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitResponse {
    private UUID fileId;
    private Boolean isDeduplicated;
    private List<ChunkUploadTask> chunks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkUploadTask {
        private Integer chunkIndex;
        private Long sizeBytes;
        private String chunkHash;
        private String primaryNodeUrl; // e.g. "http://localhost:8082/api/v1/storage/node1"
        private List<String> replicaNodeUrls; // list of replicas
    }
}
