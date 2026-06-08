package com.linkstore.metadata.service.replication;

import com.linkstore.metadata.config.RabbitMQConfig;
import com.linkstore.metadata.model.FileChunk;
import com.linkstore.metadata.repository.FileChunkRepository;
import com.linkstore.metadata.service.sharding.ConsistentHashingRouter;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ReplicationManager {

    private final FileChunkRepository chunkRepository;
    private final ConsistentHashingRouter shardingRouter;
    private final RabbitTemplate rabbitTemplate;
    private final Set<String> inactiveNodes = Collections.synchronizedSet(new HashSet<>());

    public ReplicationManager(FileChunkRepository chunkRepository,
                              ConsistentHashingRouter shardingRouter,
                              RabbitTemplate rabbitTemplate) {
        this.chunkRepository = chunkRepository;
        this.shardingRouter = shardingRouter;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void markNodeOffline(String nodeId) {
        if (inactiveNodes.add(nodeId)) {
            System.err.println("[Replication Manager] Storage Node offline: " + nodeId);
            shardingRouter.removeNode(nodeId);
            triggerReplicationFailover(nodeId);
        }
    }

    public void markNodeOnline(String nodeId) {
        if (inactiveNodes.remove(nodeId)) {
            System.out.println("[Replication Manager] Storage Node recovered online: " + nodeId);
            shardingRouter.addNode(nodeId);
        }
    }

    public Set<String> getOfflineNodes() {
        return new HashSet<>(inactiveNodes);
    }

    @Transactional
    public void triggerReplicationFailover(String failedNodeId) {
        // Find chunks hosted on the failed node
        List<FileChunk> impactedChunks = chunkRepository.findByPrimaryNodeIdOrReplicaNodeIdsContaining(failedNodeId, failedNodeId);

        for (FileChunk chunk : impactedChunks) {
            if (chunk.getPrimaryNodeId().equals(failedNodeId)) {
                // Promote replica node to primary
                String replicas = chunk.getReplicaNodeIds();
                if (replicas != null && !replicas.isEmpty()) {
                    String[] replicaArray = replicas.split(",");
                    String newPrimary = replicaArray[0]; // Promote first replica
                    
                    // Remaining replicas
                    List<String> remainingReplicas = new ArrayList<>(Arrays.asList(replicaArray));
                    remainingReplicas.remove(newPrimary);

                    // Reassign new primary and replicas
                    chunk.setPrimaryNodeId(newPrimary);
                    chunk.setReplicaNodeIds(String.join(",", remainingReplicas));
                    chunk.setStatus("REPLICATING");
                    chunkRepository.save(chunk);

                    // Publish replication repair task to RabbitMQ
                    String repairTask = chunk.getId().toString() + ":" + newPrimary + ":" + failedNodeId;
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE,
                            RabbitMQConfig.REPLICATION_ROUTING_KEY,
                            repairTask
                    );
                } else {
                    chunk.setStatus("FAILED");
                    chunkRepository.save(chunk);
                }
            } else {
                // The failed node was a replica, schedule a repair to find a new replica node
                chunk.setStatus("REPLICATING");
                chunkRepository.save(chunk);

                String repairTask = chunk.getId().toString() + ":" + chunk.getPrimaryNodeId() + ":" + failedNodeId;
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.REPLICATION_ROUTING_KEY,
                        repairTask
                );
            }
        }
    }

    @RabbitListener(queues = RabbitMQConfig.REPLICATION_QUEUE)
    @Transactional
    public void processReplicationRepair(String repairMsg) {
        // Format of message: "chunkId:sourceNode:failedNode"
        String[] parts = repairMsg.split(":");
        if (parts.length < 3) return;

        UUID chunkId = UUID.fromString(parts[0]);
        String sourceNode = parts[1];
        String failedNode = parts[2];

        chunkRepository.findById(chunkId).ifPresent(chunk -> {
            // Find a healthy node to replicate to
            List<String> healthyNodes = shardingRouter.getPhysicalNodes();
            healthyNodes.removeAll(inactiveNodes);
            healthyNodes.remove(chunk.getPrimaryNodeId());
            
            // Remove existing replicas
            if (!chunk.getReplicaNodeIds().isEmpty()) {
                healthyNodes.removeAll(Arrays.asList(chunk.getReplicaNodeIds().split(",")));
            }

            if (!healthyNodes.isEmpty()) {
                String targetReplica = healthyNodes.get(0);
                
                // Simulate physical chunk transfer from sourceNode to targetReplica
                System.out.printf("[Replication Manager] Copying chunk %s from %s to %s to repair failure of %s\n",
                        chunk.getHashSha256(), sourceNode, targetReplica, failedNode);

                // Update metadata
                String newReplicas = chunk.getReplicaNodeIds().isEmpty() 
                        ? targetReplica 
                        : chunk.getReplicaNodeIds() + "," + targetReplica;

                // Strip out the failed node if it's still in the string
                newReplicas = newReplicas.replace(failedNode, "").replace(",,", ",").replaceAll("^,", "").replaceAll(",$", "");

                chunk.setReplicaNodeIds(newReplicas);
                chunk.setStatus("ACTIVE");
                chunkRepository.save(chunk);
            }
        });
    }
}
