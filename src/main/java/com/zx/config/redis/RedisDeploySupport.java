package com.zx.config.redis;

import io.lettuce.core.ReadFrom;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将 {@link BookstoreRedisProperties} 解析为 Spring Data Redis / Redisson 可用的拓扑描述。
 */
final class RedisDeploySupport {

    private RedisDeploySupport() {
    }

    static ReadFrom resolveReadFrom(String name) {
        if (!StringUtils.hasText(name)) {
            return ReadFrom.REPLICA_PREFERRED;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return ReadFrom.valueOf(normalized);
    }

    static RedisStandaloneConfiguration standalone(BookstoreRedisProperties props) {
        BookstoreRedisProperties.Standalone s = props.getStandalone();
        Assert.hasText(s.getHost(), "bookstore.redis.standalone.host 不能为空");
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(s.getHost(), s.getPort());
        config.setDatabase(props.getDatabase());
        if (props.resolvedPassword() != null) {
            config.setPassword(props.resolvedPassword());
        }
        return config;
    }

    static RedisStaticMasterReplicaConfiguration masterReplica(BookstoreRedisProperties props) {
        BookstoreRedisProperties.MasterReplica mr = props.getMasterReplica();
        Assert.hasText(mr.getMasterHost(), "bookstore.redis.master-replica.master-host 不能为空");
        List<HostPort> replicas = parseHostPorts(mr.getNodes(), "bookstore.redis.master-replica.nodes");
        Assert.notEmpty(replicas, "bookstore.redis.master-replica.nodes 至少配置一个从节点");

        RedisStaticMasterReplicaConfiguration config =
                new RedisStaticMasterReplicaConfiguration(mr.getMasterHost(), mr.getMasterPort());
        for (HostPort replica : replicas) {
            config.addNode(replica.host(), replica.port());
        }
        if (props.resolvedPassword() != null) {
            config.setPassword(props.resolvedPassword());
        }
        return config;
    }

    static RedisSentinelConfiguration sentinel(BookstoreRedisProperties props) {
        BookstoreRedisProperties.Sentinel sentinel = props.getSentinel();
        Assert.hasText(sentinel.getMaster(), "bookstore.redis.sentinel.master 不能为空");
        List<HostPort> nodes = parseHostPorts(sentinel.getNodes(), "bookstore.redis.sentinel.nodes");
        Assert.notEmpty(nodes, "bookstore.redis.sentinel.nodes 至少配置一个 Sentinel 地址");

        Set<String> nodeStrings = new LinkedHashSet<>();
        for (HostPort hp : nodes) {
            nodeStrings.add(hp.host() + ":" + hp.port());
        }
        RedisSentinelConfiguration config = new RedisSentinelConfiguration(sentinel.getMaster(), nodeStrings);
        config.setDatabase(props.getDatabase());
        if (props.resolvedPassword() != null) {
            config.setPassword(props.resolvedPassword());
        }
        if (sentinel.resolvedPassword() != null) {
            config.setSentinelPassword(sentinel.resolvedPassword());
        }
        return config;
    }

    static RedisClusterConfiguration cluster(BookstoreRedisProperties props) {
        BookstoreRedisProperties.Cluster cluster = props.getCluster();
        List<HostPort> nodes = parseHostPorts(cluster.getNodes(), "bookstore.redis.cluster.nodes");
        Assert.notEmpty(nodes, "bookstore.redis.cluster.nodes 至少配置一个种子节点");

        RedisClusterConfiguration config = new RedisClusterConfiguration();
        for (HostPort hp : nodes) {
            config.addClusterNode(new RedisNode(hp.host(), hp.port()));
        }
        int maxRedirects = cluster.getMaxRedirects();
        Assert.isTrue(maxRedirects >= 0, "bookstore.redis.cluster.max-redirects 不能为负");
        config.setMaxRedirects(maxRedirects);
        if (props.resolvedPassword() != null) {
            config.setPassword(props.resolvedPassword());
        }
        return config;
    }

    static List<HostPort> parseHostPorts(List<String> raw, String fieldName) {
        List<HostPort> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String item : raw) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            for (String token : item.split(",")) {
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                result.add(parseHostPort(token.trim(), fieldName));
            }
        }
        return result;
    }

    static HostPort parseHostPort(String value, String fieldName) {
        int idx = value.lastIndexOf(':');
        Assert.isTrue(idx > 0 && idx < value.length() - 1,
                fieldName + " 须为 host:port，实际: " + value);
        String host = value.substring(0, idx).trim();
        int port = Integer.parseInt(value.substring(idx + 1).trim());
        Assert.hasText(host, fieldName + " host 不能为空");
        return new HostPort(host, port);
    }

    record HostPort(String host, int port) {
    }
}
