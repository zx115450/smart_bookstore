# 热点 Key 探测与加长缓存 TTL 实战

> 适用：读多写少的详情类接口（商品、内容、图书等）  
> 关键词：热点 Key · 滑动窗口 · Cache Aside · TTL 抖动 · 缓存击穿

---

## 写在前面

详情接口往往符合二八分布：少数 ID 被反复访问，绝大多数很少被点开。若所有缓存共用同一档物理 TTL（例如 30 分钟），热点 Key 会更频繁地「到期 → miss → 打库」，在高并发下放大成缓存击穿。

常见解法包括互斥重建、逻辑过期。还有一条更轻的路径：**先识别谁是热点，再给热点更长的 TTL，从概率上降低它刚好过期的次数。**

本文讲清问题、探测思路、加长 TTL 的写路径，以及和其它手段怎么组合。

---

## 一、问题：统一 TTL 对热点不公平

### 1.1 缓存击穿

```text
热点 detail:{id} 物理 TTL 到期
    ↓
同一时刻大量请求 miss
    ↓
全部打到数据库（连接池可能被打满）
```

| 手段 | 思路 | 代价 |
| --- | --- | --- |
| 互斥锁重建 | miss 时只有一人查库 | 过期瞬间首批请求变慢 |
| 逻辑过期 | 先返回旧值，异步刷新 | 短暂脏读 |
| **热点加长 TTL（本文）** | 热 Key 更晚过期 | 热数据可能更久偏旧（靠写后删收敛） |

写路径若在更新 / 下架时主动删除缓存，加长 TTL 主要服务 **读多写少**；写后仍以删除为准，而不是无限脏读。

### 1.2 为什么还要 TTL 抖动

即使同一资源 TTL 都是 7200 秒，若大量不同 Key 在同一秒写入，仍可能在同一秒集体过期，形成 **缓存雪崩**。

常见写法：

```text
实际 TTL = base + random(0, jitter)
普通：1800 + [0, 300)
热点：7200 + [0, 600)
```

---

## 二、方案总览

```text
读详情
  →（可选）布隆 / 空值缓存防穿透
  → Cache Aside：本地缓存 / Redis / DB
  → 每次成功返回：recordAccess(id)
  → 回源写入 Redis 时：resolveTtl(id)
        ├─ isHot(id) == true  → 长 TTL + 抖动
        └─ 否则               → 普通 TTL + 抖动
```

要点：

1. **探测**与**加长 TTL** 解耦：读路径只计数；写缓存时再问「是不是热点」  
2. 单机探测实现简单；多实例要全局热度需上 Redis 计数等  
3. 与逻辑过期互补：加长 TTL 降低过期频率；过期瞬间仍可用互斥 / 逻辑过期止血  

---

## 三、热点探测：分桶近似滑动窗口

### 3.1 为什么不用一个累加计数器

若每个 ID 只维护一个累计次数：

- 无法表达「最近 60 秒」  
- 曾经火过的冷数据会一直被当成热点  

需要 **滑动窗口**：只统计最近一段时间的访问。

### 3.2 分桶近似

示例参数：

| 参数 | 示例 | 含义 |
| --- | --- | --- |
| `windowSeconds` | 60 | 窗口长度 |
| `bucketSeconds` | 10 | 每个时间桶宽度 |
| `threshold` | 50 | 窗口内次数 ≥ 此值视为热点 |
| `maxTrackedKeys` | 5000 | 最多跟踪多少个 ID |

时间轴切成宽度 10 秒的桶：

```text
| b0 | b1 | b2 | b3 | b4 | b5 |  ← 约 6 个桶覆盖 60 秒
  ↑ 丢掉过期桶，只对当前窗口内桶求和
```

示意代码：

```java
// 记录访问：当前桶 +1，并删掉窗口外的旧桶
long currentBucket = nowSec / bucketSeconds;
long minBucket = currentBucket - (windowSeconds / bucketSeconds) + 1;
buckets.merge(currentBucket, 1L, Long::sum);
buckets.entrySet().removeIf(e -> e.getKey() < minBucket);

// 判定热点：窗口内求和 ≥ threshold
boolean hot = sumInWindow(id) >= threshold;
```

这是 **近似滑动窗口**：桶内不区分先后，边界误差约一个 `bucketSeconds`，实现简单、开销可控。

### 3.3 用本地 LRU / Caffeine 包住计数器

```java
Cache<Long, ConcurrentHashMap<Long, Long>> counters = Caffeine.newBuilder()
    .maximumSize(maxTrackedKeys)
    .expireAfterAccess(Duration.ofSeconds(windowSeconds * 2))
    .build();
```

作用：

- 防止恶意扫描海量 ID 把内存撑爆  
- 冷 Key 自动淘汰  

### 3.4 在哪里计数

建议在「用户真正看到详情」的成功路径计数：

```text
缓存命中返回 → recordAccess
DB 回源成功返回 → recordAccess
直接 404 / 鉴权失败 / 异常 → 不计数
```

这样热点反映真实读流量，而不是穿透或刷接口噪声。

---

## 四、加长 TTL：写缓存时分流

```java
private Duration resolveTtl(Long id) {
    if (isHot(id)) {
        return ttlWithJitter(hotTtlSeconds, hotJitterSeconds);
        // 例如 7200 + [0, 600)
    }
    return ttlWithJitter(normalTtlSeconds, normalJitterSeconds);
    // 例如 1800 + [0, 300)
}

private Duration ttlWithJitter(long baseSeconds, long jitterSeconds) {
    long base = Math.max(1, baseSeconds);
    long jitter = Math.max(0, jitterSeconds);
    long ttl = base + (jitter > 0 ? ThreadLocalRandom.current().nextLong(0, jitter) : 0);
    return Duration.ofSeconds(ttl);
}
```

### 4.1 时序直觉

```text
冷数据：
  写入 → 约 30min 过期 → 再 miss 回源

热数据（窗口内 ≥ 阈值）：
  再次 put 时识别为热点
  写入 → 约 2h 过期 → miss 频率下降
  期间若发生更新 → 主动删缓存，不靠等 TTL
```

### 4.2 为什么常常是「下次写入」才变长

第一次打爆款时，计数器可能还没到阈值，仍用普通 TTL；持续访问后 `isHot=true`，**下一次**回源或刷新写入才用长 TTL。这样可避免偶发尖刺把冷 Key 立刻按热点策略处理。

扩展：判定热点后，在缓存命中时主动 `EXPIRE` 续命（按业务需要可选）。

---

## 五、推荐配置与自测

```yaml
cache:
  hot-key-enabled: true
  hot-window-seconds: 60
  hot-bucket-seconds: 10
  hot-threshold: 50
  hot-detail-ttl-seconds: 7200
  hot-detail-ttl-jitter-seconds: 600
  detail-ttl-seconds: 1800
  detail-ttl-jitter-seconds: 300
```

自测思路：

1. 把 `hot-threshold` 临时改小，方便压测  
2. 对同一 ID 连续请求超过阈值  
3. 清掉该 Key 或等 miss 后再写入  
4. 观察 Redis `TTL` 是否进入「长 TTL」量级  

---

## 六、局限与组合拳

| 局限 | 说明 | 可选升级 |
| --- | --- | --- |
| 本机热度 | 多实例流量不均时判断不一致 | Redis `INCR` + 窗口、中心统计 |
| 近似窗口 | 桶边界有误差 | 更细的桶，或精确滑动窗口 |
| 只加长 TTL | 不解决「已过期瞬间」的并发击穿 | 再叠互斥锁或逻辑过期 |
| 热数据更久 | 读多写少依赖写后删除 | 强一致字段不要只靠长 TTL |

和其它手段怎么叠：

```text
穿透  → 布隆过滤器 / 空值短 TTL
雪崩  → TTL 抖动
击穿  → 热点长 TTL（降频）+ 互斥 / 逻辑过期（过期瞬间）
多级  → 本地缓存 + Redis
```

---

## 七、面试口述（约 1 分钟）

> 详情接口有明显热点。统一短 TTL 时，爆款 Key 过期更频繁，容易打穿数据库。读路径用本机分桶滑动窗口统计最近一分钟访问次数，超过阈值就认定热点；写 Redis 时热点用更长的 TTL，并加随机抖动防雪崩。更新下架仍走删缓存。多实例热度不共享时，可改成 Redis 计数；对可接受短暂脏读的超级热点，还可以再上逻辑过期。

---

## 八、小结

热点加长 TTL 解决的是「热 Key 过期太勤」带来的击穿压力，实现成本低，适合挂在现有 Cache Aside 上。它替代不了布隆（穿透）、也替代不了逻辑过期 / 互斥（过期瞬间），但作为第一层优化往往性价比很高。
