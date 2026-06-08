package com.linkstore.metadata.service.sharding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashingRouterTest {

    private ConsistentHashingRouter router;

    @BeforeEach
    void setUp() {
        router = new ConsistentHashingRouter();
        // Manually inject node configurations for test isolation
        ReflectionTestUtils.setField(router, "rawNodesList", "storage-node-1:8082,storage-node-2:8083,storage-node-3:8084,storage-node-4:8085");
        router.init();
    }

    @Test
    void testNodeDistribution() {
        List<String> nodes = router.getPhysicalNodes();
        assertEquals(4, nodes.size());
        assertTrue(nodes.contains("storage-node-1"));
        assertTrue(nodes.contains("storage-node-2"));

        // Route 1000 sample keys and count distribution
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "test-file-chunk-" + i;
            String node = router.getPrimaryNode(key);
            counts.put(node, counts.getOrDefault(node, 0) + 1);
        }

        // Verify that all nodes received some share of the workload (no node remains empty)
        for (String node : nodes) {
            Integer count = counts.get(node);
            assertNotNull(count, "Node " + node + " received no keys!");
            assertTrue(count > 100, "Node " + node + " has extremely low distribution: " + count);
        }
    }

    @Test
    void testNodeRemovalAndFailover() {
        String testKey = "important-user-document-chunk-0";
        
        // Initial primary routing
        String primaryNode = router.getPrimaryNode(testKey);
        assertNotNull(primaryNode);

        // Remove the assigned node
        router.removeNode(primaryNode);

        // Routing should fail over to another active node
        String newPrimaryNode = router.getPrimaryNode(testKey);
        assertNotNull(newPrimaryNode);
        assertNotEquals(primaryNode, newPrimaryNode);

        // Re-adding the node should reclaim routing ownership of the key
        router.addNode(primaryNode);
        String restoredNode = router.getPrimaryNode(testKey);
        assertEquals(primaryNode, restoredNode);
    }

    @Test
    void testReplicationAllocation() {
        String testKey = "replica-checksum-data-772";
        
        // Request primary and secondary replica mappings
        List<String> selectedNodes = router.getNodes(testKey, 2);
        
        assertEquals(2, selectedNodes.size());
        assertNotEquals(selectedNodes.get(0), selectedNodes.get(1), "Primary and replica must be distinct nodes");
    }
}
