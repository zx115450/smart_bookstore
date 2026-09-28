package com.zx.config.redis;

/**
 * Redis 部署拓扑：由 {@code bookstore.redis.mode} 选择，驱动 Lettuce / Redisson 连接方式。
 * <ul>
 *   <li>{@link #STANDALONE} — 单节点</li>
 *   <li>{@link #MASTER_REPLICA} — 静态主从（无自动故障转移；读可走从库）</li>
 *   <li>{@link #SENTINEL} — 哨兵（自动发现主节点并故障转移）</li>
 *   <li>{@link #CLUSTER} — 分片集群（按 slot 分片，水平扩展）</li>
 * </ul>
 */
public enum RedisDeployMode {

    STANDALONE,
    MASTER_REPLICA,
    SENTINEL,
    CLUSTER
}
