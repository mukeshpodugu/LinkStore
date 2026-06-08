package com.linkstore.metadata.scheduler;

import com.linkstore.metadata.service.sharding.ConsistentHashingRouter;
import com.linkstore.metadata.service.replication.ReplicationManager;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Component
public class HealthCheckerScheduler {

    private final ConsistentHashingRouter shardingRouter;
    private final ReplicationManager replicationManager;
    private final RestTemplate restTemplate;

    public HealthCheckerScheduler(ConsistentHashingRouter shardingRouter,
                                  ReplicationManager replicationManager,
                                  RestTemplateBuilder restTemplateBuilder) {
        this.shardingRouter = shardingRouter;
        this.replicationManager = replicationManager;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(2000))
                .setReadTimeout(Duration.ofMillis(2000))
                .build();
    }

    @Scheduled(fixedDelay = 10000)
    public void runHeartbeats() {
        List<String> nodes = shardingRouter.getPhysicalNodes();
        
        // Include offline nodes to check for recovery
        nodes.addAll(replicationManager.getOfflineNodes());

        for (String node : nodes) {
            String url = getNodeHealthUrl(node);
            try {
                restTemplate.getForObject(url, String.class);
                replicationManager.markNodeOnline(node);
            } catch (Exception e) {
                replicationManager.markNodeOffline(node);
            }
        }
    }

    private String getNodeHealthUrl(String nodeId) {
        switch (nodeId) {
            case "storage-node-1": return "http://localhost:8082/actuator/health";
            case "storage-node-2": return "http://localhost:8083/actuator/health";
            case "storage-node-3": return "http://localhost:8084/actuator/health";
            case "storage-node-4": return "http://localhost:8085/actuator/health";
            default: return "http://localhost:8082/actuator/health";
        }
    }
}
