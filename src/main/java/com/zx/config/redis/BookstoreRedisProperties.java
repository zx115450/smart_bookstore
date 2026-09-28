package com.zx.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 拓扑与连接参数。通过 {@code bookstore.redis.mode} 在单体 / 主从 / 哨兵 / 分片集群间切换，
 * 无需改业务代码（{@code StringRedisTemplate} / {@code RedissonClient} 仍按原样注入）。
 */
@ConfigurationProperties(prefix = "bookstore.redis")
public class BookstoreRedisProperties {

    /**
     * 部署模式：{@code standalone} | {@code master-replica} | {@code sentinel} | {@code cluster}。
     * 也可用环境变量 {@code REDIS_MODE}。
     */
    private RedisDeployMode mode = RedisDeployMode.STANDALONE;

    /** 数据节点密码（各拓扑下 Redis 实例认证）。空字符串视为无密码。 */
    private String password = "";

    /**
     * 逻辑库索引。Cluster 模式不支持 {@code SELECT}，固定使用 0；
     * 静态主从下 Lettuce 能力也有限，以实际客户端为准。
     */
    private int database = 0;

    /**
     * 主从 / 哨兵 / 集群下的读策略（Lettuce {@code ReadFrom} 名）。
     * 常用：{@code MASTER}、{@code MASTER_PREFERRED}、{@code REPLICA_PREFERRED}、{@code REPLICA}、{@code ANY}。
     */
    private String readFrom = "REPLICA_PREFERRED";

    private final Standalone standalone = new Standalone();
    private final MasterReplica masterReplica = new MasterReplica();
    private final Sentinel sentinel = new Sentinel();
    private final Cluster cluster = new Cluster();

    public RedisDeployMode getMode() {
        return mode;
    }

    public void setMode(RedisDeployMode mode) {
        this.mode = mode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(String readFrom) {
        this.readFrom = readFrom;
    }

    public Standalone getStandalone() {
        return standalone;
    }

    public MasterReplica getMasterReplica() {
        return masterReplica;
    }

    public Sentinel getSentinel() {
        return sentinel;
    }

    public Cluster getCluster() {
        return cluster;
    }

    /** 有文本才视为配置了密码。 */
    public String resolvedPassword() {
        return StringUtils.hasText(password) ? password : null;
    }

    public static class Standalone {
        private String host = "localhost";
        private int port = 6379;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /**
     * 静态主从：应用直连 master + replica 列表，无 Sentinel。
     * {@code nodes} 可为 YAML 列表，或逗号分隔的 {@code host:port}。
     */
    public static class MasterReplica {
        private String masterHost = "localhost";
        private int masterPort = 6379;
        /** 从节点，例如 {@code localhost:6380}。 */
        private List<String> nodes = new ArrayList<>();

        public String getMasterHost() {
            return masterHost;
        }

        public void setMasterHost(String masterHost) {
            this.masterHost = masterHost;
        }

        public int getMasterPort() {
            return masterPort;
        }

        public void setMasterPort(int masterPort) {
            this.masterPort = masterPort;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes != null ? nodes : new ArrayList<>();
        }
    }

    public static class Sentinel {
        /** 与 sentinel.conf 中 {@code sentinel monitor <name>} 一致。 */
        private String master = "mymaster";
        /** Sentinel 地址列表，{@code host:port}。 */
        private List<String> nodes = new ArrayList<>();
        /** Sentinel 自身密码（与数据节点密码可不同）。 */
        private String password = "";

        public String getMaster() {
            return master;
        }

        public void setMaster(String master) {
            this.master = master;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes != null ? nodes : new ArrayList<>();
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String resolvedPassword() {
            return StringUtils.hasText(password) ? password : null;
        }
    }

    /**
     * Redis Cluster：配置若干种子节点即可，客户端会通过 CLUSTER NODES 发现全拓扑。
     * 不支持多 database；跨 slot 的多 Key 命令需同 hash tag 或避免使用。
     */
    public static class Cluster {
        /** 种子节点，{@code host:port}；至少一个。 */
        private List<String> nodes = new ArrayList<>();
        /** MOVED/ASK 重定向最大次数。 */
        private int maxRedirects = 5;

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes != null ? nodes : new ArrayList<>();
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }
    }
}
