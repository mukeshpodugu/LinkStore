package com.linkstore.metadata.controller;

import com.linkstore.metadata.config.HeaderAuthenticationFilter.UserPrincipal;
import com.linkstore.metadata.dto.DashboardAdminResponse;
import com.linkstore.metadata.dto.DashboardAdminResponse.CacheStatsDto;
import com.linkstore.metadata.dto.DashboardAdminResponse.NodeStatusDto;
import com.linkstore.metadata.dto.DashboardAdminResponse.SystemMetricsDto;
import com.linkstore.metadata.dto.DashboardUserResponse;
import com.linkstore.metadata.model.User;
import com.linkstore.metadata.repository.*;
import com.linkstore.metadata.service.replication.ReplicationManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final UserRepository userRepository;
    private final FileMetadataRepository fileRepository;
    private final SharedLinkRepository sharedLinkRepository;
    private final AuditLogRepository auditLogRepository;
    private final FileChunkRepository chunkRepository;
    private final ReplicationManager replicationManager;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardController(UserRepository userRepository,
                               FileMetadataRepository fileRepository,
                               SharedLinkRepository sharedLinkRepository,
                               AuditLogRepository auditLogRepository,
                               FileChunkRepository chunkRepository,
                               ReplicationManager replicationManager,
                               RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.sharedLinkRepository = sharedLinkRepository;
        this.auditLogRepository = auditLogRepository;
        this.chunkRepository = chunkRepository;
        this.replicationManager = replicationManager;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/user")
    public ResponseEntity<DashboardUserResponse> getUserDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        long filesCount = fileRepository.findByOwnerIdAndIsDeletedFalse(user.getId()).size();
        long sharedCount = sharedLinkRepository.findByCreatorId(user.getId()).size();

        // Convert AuditLog to RecentActivity
        List<DashboardUserResponse.RecentActivity> activities = new ArrayList<>();
        auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().limit(10).forEach(log -> {
                    activities.add(DashboardUserResponse.RecentActivity.builder()
                            .action(log.getAction())
                            .details(log.getDetails())
                            .timestamp(log.getCreatedAt().toString())
                            .build());
                });

        DashboardUserResponse response = DashboardUserResponse.builder()
                .storageQuotaBytes(user.getStorageQuotaBytes())
                .storageUsedBytes(user.getStorageUsedBytes())
                .storageRemainingBytes(Math.max(0, user.getStorageQuotaBytes() - user.getStorageUsedBytes()))
                .filesCount(filesCount)
                .sharedLinksCount(sharedCount)
                .recentActivities(activities)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardAdminResponse> getAdminDashboard() {
        long activeUsers = userRepository.count();
        long totalFiles = fileRepository.count();
        long totalStorageUsed = userRepository.findAll().stream()
                .mapToLong(User::getStorageUsedBytes).sum();

        // Node Health
        List<NodeStatusDto> nodes = new ArrayList<>();
        String[] storageNodes = {"storage-node-1", "storage-node-2", "storage-node-3", "storage-node-4"};
        int port = 8082;
        for (String node : storageNodes) {
            boolean isOffline = replicationManager.getOfflineNodes().contains(node);
            long chunks = chunkRepository.findByPrimaryNodeIdOrReplicaNodeIdsContaining(node, node)
                    .stream().filter(c -> c.getPrimaryNodeId().equals(node)).count();

            nodes.add(NodeStatusDto.builder()
                    .nodeId(node)
                    .status(isOffline ? "DOWN" : "UP")
                    .url("http://localhost:" + port)
                    .chunkCount(chunks)
                    .storageUsedBytes(chunks * 2097152L) // Estimated 2MB per chunk
                    .build());
            port++;
        }

        // Cache Statistics from Redis
        Object hitsObj = redisTemplate.opsForValue().get("linkstore:cache:hits");
        Object missesObj = redisTemplate.opsForValue().get("linkstore:cache:misses");
        long hits = hitsObj != null ? Long.parseLong(hitsObj.toString()) : 0L;
        long misses = missesObj != null ? Long.parseLong(missesObj.toString()) : 0L;
        double hitRatio = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses);

        CacheStatsDto cacheStats = CacheStatsDto.builder()
                .hits(hits)
                .misses(misses)
                .hitRatio(hitRatio)
                .cachedSessionsCount(activeUsers) // Simplified metric
                .build();

        // System throughput metrics simulation
        SystemMetricsDto systemMetrics = SystemMetricsDto.builder()
                .uploadCount24h(totalFiles)
                .downloadCount24h(hits)
                .systemThroughputMbps(120.5)
                .errorRatePercentage(replicationManager.getOfflineNodes().isEmpty() ? 0L : 25L)
                .build();

        DashboardAdminResponse response = DashboardAdminResponse.builder()
                .activeUsersCount(activeUsers)
                .totalFilesCount(totalFiles)
                .totalStorageUsedBytes(totalStorageUsed)
                .nodes(nodes)
                .cacheStats(cacheStats)
                .systemMetrics(systemMetrics)
                .build();

        return ResponseEntity.ok(response);
    }
}
