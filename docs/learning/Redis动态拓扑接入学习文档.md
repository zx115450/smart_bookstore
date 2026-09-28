# Redis 动态拓扑：单体 / 主从 / 哨兵 / 分片集群接入

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 配置与装配实战  
> **关联包**：`com.zx.config.redis`  
> **建议前置**：[Redis 缓存查询三种方案](../Redis缓存查询三种方案学习文档.md) · [缓存](./缓存.md)

## 学习目标

1. 分清 Redis **单体、静态主从、哨兵、分片集群** 各自解决什么问题
2. 会用 `bookstore.redis.mode` 切换连接方式，且不改业务代码
3. 理解本仓库如何同时装配 Lettuce（`StringRedisTemplate`）与 Redisson
4. 知道 Cluster 相对哨兵的额外约束（无 `SELECT`、跨 slot 多 Key 限制）

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| `BookstoreRedisProperties` | `bookstore.redis.*` 配置绑定 |
| `RedisDeployConfiguration` | 按 mode 创建 `RedisConnectionFactory` + `RedissonClient` |
| `RedisDeploySupport` | 解析 `host:port`、构建四种 `*Configuration` |
| `application.yaml` → `bookstore.redis` | 默认 `standalone`，可用环境变量覆盖 |
| `application-local.yaml.example` | 本地切换示例注释 |
| `RedisDeploySupportTest` | 解析与配置构建单测 |

业务侧（秒杀、缓存、签到、AI Memory 等）继续注入 `StringRedisTemplate`，**不感知**底层拓扑。

---

## 一、为什么要「动态选拓扑」

本地开发通常只要一台 Redis；预发 / 生产可能要：

- **读多写少** → 主从，读尽量打到从库
- **主节点宕机可自动切换** → 哨兵（Sentinel）
- **数据与 QPS 水平扩展** → 分片集群（Cluster）
- **开发机 / CI** → 继续单体，省资源

若把连接方式写死在代码里，换环境就要改 Java。正确做法是：**配置决定拓扑，业务只依赖 Spring 的连接抽象**。

本仓库用一个开关完成切换：

```text
bookstore.redis.mode = standalone | master-replica | sentinel | cluster
```

等价环境变量：`REDIS_MODE`。

---

## 二、四种模式对比

| 模式 | 配置值 | 分片 | 故障转移 | 读从库 | 典型场景 |
| --- | --- | --- | --- | --- | --- |
| 单体 | `standalone` | 无 | 无 | 无 | 本地、单测、小流量 |
| 静态主从 | `master-replica` | 无 | 无（主挂了要人工切） | 可（`ReadFrom`） | 固定主从地址、过渡环境 |
| 哨兵 | `sentinel` | 无 | 有（Sentinel 选新主） | 可 | 单分片高可用 |
| 分片集群 | `cluster` | 有（16384 slots） | 有（集群内选举） | 可 | 大数据量 / 高吞吐水平扩展 |

概念关系：

```text
standalone     应用 ──► 唯一 Redis

master-replica 应用 ──写──► Master
               └─读──► Replica(s)   （无自动 failover）

sentinel       应用 ──► Sentinel(s) ──► 当前 Master（可切主，仍是一份数据）

cluster        应用 ──► 种子节点 ──CLUSTER NODES──► 多 Master 分片
               Key → CRC16 % 16384 → 落到某个 slot / Master
```

注意：日常说的「主从」若带自动切主，一般是 **复制 + 哨兵**；要分片扩容才上 **Cluster**。本仓库 `master-replica` 特指静态主从。

---

## 三、配置项说明

根前缀：`bookstore.redis`。

| 配置项 | 含义 | 默认 / 示例 |
| --- | --- | --- |
| `mode` | 拓扑 | `standalone`（`REDIS_MODE`） |
| `password` | 数据节点密码 | 空 = 无密码（`REDIS_PASSWORD`） |
| `database` | 逻辑库 | `0`；**Cluster 忽略非 0**（不支持 `SELECT`） |
| `read-from` | 主从/哨兵/集群读策略 | `REPLICA_PREFERRED` |
| `standalone.host` / `port` | 单节点 | `localhost:6379` |
| `master-replica.master-host` / `master-port` | 主节点 | — |
| `master-replica.nodes` | 从节点 `host:port` | 可逗号分隔 |
| `sentinel.master` | 监控名 | `mymaster` |
| `sentinel.nodes` | Sentinel 地址 | 如 `host:26379` |
| `sentinel.password` | Sentinel 密码 | 可与数据节点不同 |
| `cluster.nodes` | Cluster 种子节点 | 至少一个 `host:port` |
| `cluster.max-redirects` | MOVED/ASK 最大重定向次数 | `5` |

`spring.data.redis.*` 仍保留作兼容占位；**真正建连以 `bookstore.redis` 为准**。

### 3.1 `read-from` 常用值

| 值 | 行为 |
| --- | --- |
| `MASTER` | 只读主 |
| `MASTER_PREFERRED` | 优先主，主不可用再从 |
| `REPLICA_PREFERRED` | 优先从，无从可用再主（默认） |
| `REPLICA` | 只读从 |
| `ANY` | 任意节点 |

写仍走对应 slot 的主节点；该选项主要影响读路由。

---

## 四、配置示例

### 4.1 单体（默认）

```yaml
bookstore:
  redis:
    mode: standalone
    password:
    standalone:
      host: localhost
      port: 6379
```

### 4.2 静态主从

```yaml
bookstore:
  redis:
    mode: master-replica
    password: your-redis-password
    read-from: REPLICA_PREFERRED
    master-replica:
      master-host: redis-master
      master-port: 6379
      nodes:
        - redis-replica-1:6379
        - redis-replica-2:6379
```

### 4.3 哨兵

```yaml
bookstore:
  redis:
    mode: sentinel
    password: your-redis-password
    read-from: REPLICA_PREFERRED
    sentinel:
      master: mymaster
      nodes:
        - sentinel-1:26379
        - sentinel-2:26379
        - sentinel-3:26379
      password: your-sentinel-password
```

### 4.4 分片集群

```yaml
bookstore:
  redis:
    mode: cluster
    password: your-redis-password
    database: 0                 # 必须为 0；非 0 会打 warn 并忽略
    read-from: REPLICA_PREFERRED
    cluster:
      nodes:
        - redis-cluster-1:7000
        - redis-cluster-2:7001
        - redis-cluster-3:7002
      max-redirects: 5
```

只需配置**种子节点**；客户端通过 `CLUSTER NODES` 发现其余节点与 slot 映射。生产建议配多个种子，避免单点种子宕机导致启动失败。

---

## 五、装配与工作原理

### 5.1 启动链路

```text
application.yaml / 环境变量
        │
        ▼
BookstoreRedisProperties（mode + 各拓扑参数）
        │
        ▼
RedisDeployConfiguration
   ├─ @Primary RedisConnectionFactory（Lettuce）
   │     → StringRedisTemplate / RedisTemplate
   └─ RedissonClient（同 mode）
```

### 5.2 mode 映射

| mode | Lettuce / Spring Data | Redisson |
| --- | --- | --- |
| `standalone` | `RedisStandaloneConfiguration` | `useSingleServer()` |
| `master-replica` | `RedisStaticMasterReplicaConfiguration` + `ReadFrom` | `useMasterSlaveServers()` |
| `sentinel` | `RedisSentinelConfiguration` + `ReadFrom` | `useSentinelServers()` |
| `cluster` | `RedisClusterConfiguration` + `ReadFrom` | `useClusterServers()` |

### 5.3 Cluster 业务注意（比换连接更重要）

换 `mode=cluster` 后连接能通，但部分用法会踩坑：

1. **不支持多 database**：只能用 db 0
2. **跨 slot 多 Key 命令**：如无 hash tag 的 `MGET`、`DEL` 多 key、部分事务 / 管道，可能报 `CROSSSLOT`
3. **Lua**：脚本触及的 key 通常须在同一 slot（常用 `{tag}` 做 hash tag）
4. **本仓库现有 Key**（秒杀、布隆、ZSET 逾期等）在单机设计下一般单 key 操作较多，上 Cluster 前仍建议按业务扫一遍多 key / 阻塞命令

业务注入方式不变：

```java
private final StringRedisTemplate redis;
```

---

## 六、怎么选

| 环境 | 建议 mode | 说明 |
| --- | --- | --- |
| 本地 / CI | `standalone` | 与 `docker compose` 单 Redis 一致 |
| 固定主从、暂无 Sentinel | `master-replica` | 无自动切主 |
| 单分片高可用 | `sentinel` | 运维先搭 Sentinel |
| 需要水平分片扩容 | `cluster` | 运维先搭 Cluster；应用配种子节点即可 |

读写分离：强一致读用 `MASTER` / `MASTER_PREFERRED`；可容忍复制延迟用 `REPLICA_PREFERRED`。

---

## 七、验证与排障

1. 日志：`Redis Lettuce mode=...` / `Redis Redisson mode=...`
2. 启动断言：主从未配从、哨兵/集群 nodes 为空会直接失败
3. Cluster 连不上时先查：
   - 种子端口是否为集群总线数据口（常见 `7000+`，不是随便一个单机 `6379`）
   - 节点间 `cluster-announce-ip` / 网络是否互通（容器尤其容易踩坑）
   - `database` 是否误配为非 0
4. 运行期 `MOVED` / `ASK`：属正常重定向；`max-redirects` 过小可能失败
5. 单测：`mvn -Dtest=RedisDeploySupportTest test`

本地开发仍只需：

```bash
docker compose up -d redis
```

不必为开发起 Cluster。

---

## 八、源码阅读顺序

1. `BookstoreRedisProperties` / `RedisDeployMode`
2. `RedisDeploySupport`（含 `cluster(...)`）
3. `RedisDeployConfiguration` 的 `switch`
4. 任意 `*RedisService`（确认只用 `StringRedisTemplate`）
5. `application.yaml` 中 `bookstore.redis` 段

---

## 参考

- [Spring Data Redis · Connection Modes](https://docs.spring.io/spring-data-redis/reference/redis/connection-modes.html)
- 本仓库 `README.md` 中间件连接说明
- [缓存增强分步实现指南](./缓存增强分步实现指南.md)（业务缓存，与拓扑正交）
