package com.linkstore.metadata.service.sharding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ConsistentHashingRouter {

    private final TreeMap<Long, String> hashRing = new TreeMap<>();
    private final int numberOfReplicas = 100; // Number of virtual nodes per physical node

    @Value("${storage.nodes.list}")
    private String rawNodesList; // format: "node1:port,node2:port"

    private final List<String> physicalNodes = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (rawNodesList != null && !rawNodesList.isEmpty()) {
            String[] nodes = rawNodesList.split(",");
            for (String node : nodes) {
                String nodeName = node.split(":")[0];
                addNode(nodeName);
            }
        }
    }

    // Add a physical storage node to the ring with virtual nodes
    public synchronized void addNode(String node) {
        physicalNodes.add(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(node + "-vnode-" + i);
            hashRing.put(hash, node);
        }
    }

    // Remove a physical storage node from the ring
    public synchronized void removeNode(String node) {
        physicalNodes.remove(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(node + "-vnode-" + i);
            hashRing.remove(hash);
        }
    }

    // Get primary storage node for a specific key (chunk hash)
    public String getPrimaryNode(String key) {
        if (hashRing.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        if (!hashRing.containsKey(hash)) {
            SortedMap<Long, String> tailMap = hashRing.tailMap(hash);
            hash = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
        }
        return hashRing.get(hash);
    }

    // Get primary and replica nodes for a key. Returns unique list.
    public List<String> getNodes(String key, int replicationFactor) {
        List<String> selectedNodes = new ArrayList<>();
        if (hashRing.isEmpty()) {
            return selectedNodes;
        }

        long hash = hash(key);
        int maxAttempts = hashRing.size();
        int attempts = 0;

        while (selectedNodes.size() < replicationFactor && attempts < maxAttempts) {
            SortedMap<Long, String> tailMap = hashRing.tailMap(hash);
            long nodeHash = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
            String candidateNode = hashRing.get(nodeHash);

            if (!selectedNodes.contains(candidateNode)) {
                selectedNodes.add(candidateNode);
            }
            
            // Move clockwise past the current virtual node
            hash = nodeHash + 1;
            attempts++;
        }

        return selectedNodes;
    }

    // Helper hashing function returning a 32-bit/64-bit integer hash from MD5/SHA-256
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Wrap first 8 bytes into a long
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (bytes[i] & 0xff);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public List<String> getPhysicalNodes() {
        return new ArrayList<>(physicalNodes);
    }
}
