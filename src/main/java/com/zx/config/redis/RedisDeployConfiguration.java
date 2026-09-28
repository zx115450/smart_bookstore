package com.zx.config.redis;

import io.lettuce.core.ReadFrom;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 按 {@link BookstoreRedisProperties#getMode()} 动态创建 Lettuce 连接工厂与 RedissonClient。
 * <p>
 * 声明 {@link RedisConnectionFactory} 后，Spring Boot Data Redis 自动配置会让步；
 * 声明 {@link RedissonClient} 后，Redisson Starter 不再按「仅 spring.data.redis.host」建单机客户端。
 */
@Configuration
@EnableConfigurationProperties(BookstoreRedisProperties.class)
public class RedisDeployConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisDeployConfiguration.class);

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(BookstoreRedisProperties props) {
        LettuceConnectionFactory factory = switch (props.getMode()) {
            case STANDALONE -> new LettuceConnectionFactory(RedisDeploySupport.standalone(props));
            case MASTER_REPLICA -> new LettuceConnectionFactory(
                    RedisDeploySupport.masterReplica(props),
                    lettuceClientConfig(props));
            case SENTINEL -> new LettuceConnectionFactory(
                    RedisDeploySupport.sentinel(props),
                    lettuceClientConfig(props));
            case CLUSTER -> {
                warnIfClusterDatabaseNonZero(props);
                yield new LettuceConnectionFactory(
                        RedisDeploySupport.cluster(props),
                        lettuceClientConfig(props));
            }
        };
        log.info("Redis Lettuce mode={}, readFrom={}", props.getMode(), props.getReadFrom());
        return factory;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(BookstoreRedisProperties props) {
        Config config = new Config();
        String password = props.resolvedPassword();
        switch (props.getMode()) {
            case STANDALONE -> {
                var s = props.getStandalone();
                var server = config.useSingleServer()
                        .setAddress(toRedisUri(s.getHost(), s.getPort()))
                        .setDatabase(props.getDatabase());
                if (password != null) {
                    server.setPassword(password);
                }
            }
            case MASTER_REPLICA -> {
                var mr = props.getMasterReplica();
                var servers = config.useMasterSlaveServers()
                        .setMasterAddress(toRedisUri(mr.getMasterHost(), mr.getMasterPort()))
                        .setDatabase(props.getDatabase());
                for (RedisDeploySupport.HostPort replica : RedisDeploySupport.parseHostPorts(
                        mr.getNodes(), "bookstore.redis.master-replica.nodes")) {
                    servers.addSlaveAddress(toRedisUri(replica.host(), replica.port()));
                }
                if (password != null) {
                    servers.setPassword(password);
                }
            }
            case SENTINEL -> {
                var sentinel = props.getSentinel();
                var servers = config.useSentinelServers()
                        .setMasterName(sentinel.getMaster())
                        .setDatabase(props.getDatabase());
                for (RedisDeploySupport.HostPort node : RedisDeploySupport.parseHostPorts(
                        sentinel.getNodes(), "bookstore.redis.sentinel.nodes")) {
                    servers.addSentinelAddress(toRedisUri(node.host(), node.port()));
                }
                if (password != null) {
                    servers.setPassword(password);
                }
                if (sentinel.resolvedPassword() != null) {
                    servers.setSentinelPassword(sentinel.resolvedPassword());
                }
            }
            case CLUSTER -> {
                warnIfClusterDatabaseNonZero(props);
                List<RedisDeploySupport.HostPort> seedNodes = RedisDeploySupport.parseHostPorts(
                        props.getCluster().getNodes(), "bookstore.redis.cluster.nodes");
                Assert.notEmpty(seedNodes, "bookstore.redis.cluster.nodes 至少配置一个种子节点");
                var servers = config.useClusterServers();
                for (RedisDeploySupport.HostPort node : seedNodes) {
                    servers.addNodeAddress(toRedisUri(node.host(), node.port()));
                }
                if (password != null) {
                    servers.setPassword(password);
                }
            }
        }
        log.info("Redis Redisson mode={}", props.getMode());
        return Redisson.create(config);
    }

    private static LettuceClientConfiguration lettuceClientConfig(BookstoreRedisProperties props) {
        ReadFrom readFrom = RedisDeploySupport.resolveReadFrom(props.getReadFrom());
        return LettuceClientConfiguration.builder()
                .readFrom(readFrom)
                .build();
    }

    private static void warnIfClusterDatabaseNonZero(BookstoreRedisProperties props) {
        if (props.getDatabase() != 0) {
            log.warn("Redis Cluster 不支持 SELECT database={}，将忽略该配置并使用 db 0",
                    props.getDatabase());
        }
    }

    private static String toRedisUri(String host, int port) {
        return "redis://" + host + ":" + port;
    }
}
