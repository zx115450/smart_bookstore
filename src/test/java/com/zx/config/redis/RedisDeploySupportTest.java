package com.zx.config.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisDeploySupportTest {

    @Test
    void parseHostPorts_supportsCommaSeparatedEntry() {
        List<RedisDeploySupport.HostPort> nodes = RedisDeploySupport.parseHostPorts(
                List.of("a:6379,b:6380", "c:6381"), "nodes");
        assertEquals(3, nodes.size());
        assertEquals("b", nodes.get(1).host());
        assertEquals(6380, nodes.get(1).port());
    }

    @Test
    void standalone_buildsHostPortAndDatabase() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.setMode(RedisDeployMode.STANDALONE);
        props.setDatabase(2);
        props.getStandalone().setHost("redis-a");
        props.getStandalone().setPort(6389);

        RedisStandaloneConfiguration config = RedisDeploySupport.standalone(props);
        assertEquals("redis-a", config.getHostName());
        assertEquals(6389, config.getPort());
        assertEquals(2, config.getDatabase());
    }

    @Test
    void masterReplica_requiresReplicaNodes() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.setMode(RedisDeployMode.MASTER_REPLICA);
        props.getMasterReplica().setMasterHost("master");
        props.getMasterReplica().setMasterPort(6379);

        assertThrows(IllegalArgumentException.class, () -> RedisDeploySupport.masterReplica(props));
    }

    @Test
    void masterReplica_addsReplicas() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.getMasterReplica().setMasterHost("master");
        props.getMasterReplica().setMasterPort(6379);
        props.getMasterReplica().setNodes(List.of("replica:6380"));

        RedisStaticMasterReplicaConfiguration config = RedisDeploySupport.masterReplica(props);
        assertInstanceOf(RedisStaticMasterReplicaConfiguration.class, config);
        assertTrue(config.getNodes().size() >= 2);
    }

    @Test
    void sentinel_buildsMasterAndNodes() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.getSentinel().setMaster("mymaster");
        props.getSentinel().setNodes(List.of("s1:26379", "s2:26380"));

        RedisSentinelConfiguration config = RedisDeploySupport.sentinel(props);
        assertEquals("mymaster", config.getMaster().getName());
        assertEquals(2, config.getSentinels().size());
    }

    @Test
    void cluster_requiresSeedNodes() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.setMode(RedisDeployMode.CLUSTER);

        assertThrows(IllegalArgumentException.class, () -> RedisDeploySupport.cluster(props));
    }

    @Test
    void cluster_addsSeedNodesAndMaxRedirects() {
        BookstoreRedisProperties props = new BookstoreRedisProperties();
        props.getCluster().setNodes(List.of("c1:7000", "c2:7001,c3:7002"));
        props.getCluster().setMaxRedirects(8);

        RedisClusterConfiguration config = RedisDeploySupport.cluster(props);
        assertEquals(3, config.getClusterNodes().size());
        assertEquals(8, config.getMaxRedirects());
    }
}
