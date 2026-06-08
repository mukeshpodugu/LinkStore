package com.linkstore.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAdminResponse {
    private Long activeUsersCount;
    private Long totalFilesCount;
    private Long totalStorageUsedBytes;
    private List<NodeStatusDto> nodes;
    private CacheStatsDto cacheStats;
    private SystemMetricsDto systemMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeStatusDto {
        private String nodeId;
        private String status; // UP, DOWN
        private String url;
        private Long chunkCount;
        private Long storageUsedBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheStatsDto {
        private Long hits;
        private Long misses;
        private Double hitRatio;
        private Long cachedSessionsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemMetricsDto {
        private Long uploadCount24h;
        private Long downloadCount24h;
        private Double systemThroughputMbps;
        private Long errorRatePercentage;
    }
}
