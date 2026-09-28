# Elasticsearch 学习文档


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：[缓存](./缓存.md) · [Spring Boot 使用 Binlog 学习文档](./SpringBoot-Binlog学习文档.md) · [向量检索策略与存储单元](../向量检索策略与存储单元.md) · [AI 模块学习文档](../AI模块学习文档.md)  
> **仓库现状**：书目检索当前为 MySQL `LIKE`（见 `BookRepository`）；规划文档将 Elasticsearch 列为「暂不建议做」，本篇作演进与面试加深用

Elasticsearch（下文简称 ES）是基于 Lucene 的分布式搜索与分析引擎，擅长全文检索、多条件组合查询与聚合统计。本文从心智模型讲到 Query DSL、中文分词、与 MySQL 同步，以及和本仓库书城 / AI 检索的边界划分。

---

## 目录

1. [学习目标](#1-学习目标)
2. [与本项目的映射](#2-与本项目的映射)
3. [为什么需要 ES](#3-为什么需要-es)
4. [核心概念](#4-核心概念)
5. [倒排索引原理](#5-倒排索引原理)
6. [近实时、Refresh 与 Flush](#6-近实时refresh-与-flush)
7. [Mapping 与 Analyzer](#7-mapping-与-analyzer)
8. [文档 CRUD](#8-文档-crud)
9. [Query DSL 精要](#9-query-dsl-精要)
10. [相关性与排序](#10-相关性与排序)
11. [聚合 Aggregation](#11-聚合-aggregation)
12. [与 MySQL 的数据同步](#12-与-mysql-的数据同步)
13. [Spring Boot 集成要点](#13-spring-boot-集成要点)
14. [书城落地设计草案](#14-书城落地设计草案)
15. [ES vs 本仓库其它检索](#15-es-vs-本仓库其它检索)
16. [运维与常见坑](#16-运维与常见坑)
17. [分阶段学习路线](#17-分阶段学习路线)
18. [能力自检与面试口述](#18-能力自检与面试口述)（含 [自检参考答案](#184-自检参考答案)）

---

## 1. 学习目标

学完后应能：

1. 用自己的话说明：索引、文档、分片、副本、倒排索引各自解决什么问题
2. 区分 `term` / `match` / `filter` / `bool`，并写出「书名关键词 + 分类 + 只查上架」的查询
3. 说明中文场景为何要自定义 Analyzer（如 IK），以及 `text` 与 `keyword` 的分工
4. 画出 MySQL → ES 的同步方案（双写 / 监听 binlog / 定时全量），并指出一致性窗口
5. 说清：本仓库现在为何用 `LIKE` 够用、何时该上 ES、ES 与 Milvus（向量）各管什么

预计投入：2～3 周（含本地 Docker 起集群、手敲 Query DSL）。

---

## 2. 与本项目的映射

| 概念 | 本仓库现状 / 可演进落点 |
| --- | --- |
| 书目关键词搜索 | `BookRepository.pageEnabled`：`like(Book::getTitle, keyword)` |
| 列表接口 | `BookController` → `BookCatalogService.listBooks` |
| AI 查书 Tool | `BookSearchTool` 等走业务 Service / SQL，非 ES |
| 语义召回 | Milvus + Embedding（RAG），见向量检索文档 |
| 规划态度 | [书城系统功能规划](../书城系统功能规划.md)：「全文检索 ES（关键词 LIKE 够用）」暂缓 |

对照阅读建议：

1. `BookRepository` 里 `like` 的能力边界（前缀/模糊、无法良好相关度排序）
2. `BookSearchTool`：AI 侧「查书」对检索质量的依赖
3. 本文第 14、15 节：若演进 ES，如何与 Redis 缓存、Milvus 分工

---

## 3. 为什么需要 ES

### 3.1 MySQL `LIKE` 的极限

本仓库典型写法：

```text
WHERE title LIKE '%Redis%'
```

| 点 | MySQL `LIKE '%xx%'` | Elasticsearch |
| --- | --- | --- |
| 索引利用 | 前导 `%` 通常无法走 B+Tree 高效检索 | 倒排索引，按词查文档 |
| 相关度 | 基本没有「更匹配排前面」 | BM25 等打分排序 |
| 分词 | 无真正中文分词检索 | Analyzer + 分词器 |
| 多字段搜索 | SQL 难写且慢 | `multi_match` 自然 |
| 聚合分析 | 大表 GROUP BY 压力大 | 为搜索分析优化 |
| 事务 / 强一致 | 擅长 | 弱于关系库，搜索引擎定位 |

书目量小、只搜标题时，`LIKE` 完全合理。当出现下列信号，再评估 ES：

- 要搜标题 + 作者 + 简介，并按相关度排序
- 需要高亮、拼音、纠错、联想
- 检索 QPS 升高，模糊查询拖慢 MySQL
- 运营要做分类面（facet）、销量/热度组合排序

### 3.2 ES 擅长什么、不擅长什么

**擅长**

- 全文检索与相关度排序
- 多条件组合过滤（价格区间、分类、状态）
- 聚合统计（按分类计数、直方图）
- 近实时搜索（秒级可见，非毫秒强一致）

**不擅长 / 不该用它当**

- 唯一事务真相源（订单金额、库存扣减仍以 MySQL 为准）
- 频繁单文档强一致更新的账务系统
- 深度分页到十万页（需 `search_after` / 滚动，而非从 `from=100000` 硬翻）

口诀：**MySQL 管真相与事务，ES 管找得到、排得准；Redis 管热点快读。**

---

## 4. 核心概念

### 4.1 概念对照表

| ES 概念 | 粗略类比 | 说明 |
| --- | --- | --- |
| Cluster | 集群 | 一个或多个节点组成 |
| Node | 进程实例 | 存数据、参与计算 |
| Index | 「库/表」的搜索侧容器 | 逻辑命名空间，如 `books` |
| Document | 一行 JSON 文档 | 有 `_id`，字段可嵌套 |
| Field | 列 | 有类型与是否分词等 |
| Shard（主分片） | 水平拆分 | 索引数据的切片，写入落主分片 |
| Replica（副本分片） | 副本 | 提高可读与容灾，不增加「写入容量」意义上的主分片数 |
| Mapping | 表结构 | 字段类型、分词器等 |
| Alias | 别名 | 零停机切换索引的常用手段 |

说明：早期 ES 还有 Type，7.x 起弱化、8.x 移除；现在 **一个 Index 一种文档模型** 即可。

### 4.2 集群与分片示意

```text
                    Index: books
                 （设置 3 主分片 + 1 副本）
        ┌──────────────┬──────────────┬──────────────┐
        │   Shard 0    │   Shard 1    │   Shard 2    │
        │  Primary     │  Primary     │  Primary     │
        │  + Replica   │  + Replica   │  + Replica   │
        └──────────────┴──────────────┴──────────────┘
                 分布在不同 Node 上（副本不与主同节点）
```

- **主分片数**：索引创建时基本定下（后续改主分片数通常要 reindex）
- **副本数**：可动态调整
- 查询：可在主或副本上执行（默认）
- 写入：先写主分片，再并行复制到副本（可配置刷新策略）

### 4.3 文档模型示例（书城）

```json
{
  "bookId": 10086,
  "title": "Redis 设计与实现",
  "author": "黄健宏",
  "categoryId": 3,
  "categoryName": "计算机",
  "description": "深入讲解 Redis 内部数据结构与持久化……",
  "status": 1,
  "saleStock": 12,
  "borrowStock": 3,
  "shelfLocation": "3F-A-12",
  "updatedAt": "2026-08-01T10:00:00Z"
}
```

`_id` 建议直接用业务主键字符串（如 `"10086"`），便于幂等更新与按 id 删除。

---

## 5. 倒排索引原理

正排：文档 → 词项列表。  
倒排：词项 → 出现该词的文档列表（Posting List）。

```text
文档1: "Redis 设计与实现"
文档2: "Redis 实战"
文档3: "Java 并发编程实战"

分词后（示意）:
  redis → [1, 2]
  设计 → [1]
  实现 → [1]
  实战 → [2, 3]
  java → [3]
  并发 → [3]
  编程 → [3]
```

搜索 `"Redis 实战"` 时：

1. 分析查询串 → 词项 `redis`、`实战`
2. 查倒排表取 posting
3. 按查询类型做交集 / 打分（如 `match` 的 should / must 语义）
4. 返回 Top-K 文档

这也是为何「分词器选错」会导致搜不到：索引时切词与查询时切词必须一致（同一 Analyzer 链路）。

---

## 6. 近实时、Refresh 与 Flush

ES 常被称为 **近实时（NRT）** 搜索，不是写完立刻在所有副本上可搜。

| 操作 | 作用 |
| --- | --- |
| Index API 写入 | 先写入内存 buffer（及事务日志） |
| Refresh | 把内存中的段变成可被搜索的 Lucene 段；默认约 1s |
| Flush | 把内存与事务日志持久化到磁盘，更偏持久性 |

```text
写入文档
  → 内存 buffer + translog
  → refresh（默认 ~1s）→ 可被搜索
  → flush → 落盘更久安全
```

业务含义：

- 下单后「立刻」以 ES 为库存真相 → **错误**；库存仍看 MySQL
- 图书上架后 1～2 秒内搜索可见 → 通常可接受
- 若演示「写下立刻搜到」，可对单次索引请求带 `refresh=true`（有性能代价，勿滥用）

---

## 7. Mapping 与 Analyzer

### 7.1 动态 Mapping vs 显式 Mapping

- **动态**：写入 JSON 时 ES 猜类型（字符串可能变成 `text` + 子字段 `keyword`）
- **显式**：生产推荐，避免字段类型被第一条脏数据定错

书城示例 Mapping（逻辑示意）：

```json
{
  "mappings": {
    "properties": {
      "bookId": { "type": "long" },
      "title": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": { "type": "keyword", "ignore_above": 256 }
        }
      },
      "author": {
        "type": "text",
        "analyzer": "ik_smart",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "categoryId": { "type": "long" },
      "status": { "type": "integer" },
      "description": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "saleStock": { "type": "integer" },
      "updatedAt": { "type": "date" }
    }
  }
}
```

### 7.2 `text` vs `keyword`

| 类型 | 是否分词 | 典型用途 |
| --- | --- | --- |
| `text` | 是 | 全文检索：标题、简介 |
| `keyword` | 否（整值） | 精确过滤、排序、聚合：分类名、ISBN、状态码字符串 |

同一字段常用 **多字段（multi-fields）**：`title` 用于搜，`title.keyword` 用于精确与聚合。

### 7.3 分析链（Analyzer）

Analyzer ≈ Character Filters → Tokenizer → Token Filters。

中文常见方案：

| 分词器 | 特点 |
| --- | --- |
| IK（`ik_max_word` / `ik_smart`） | 国内最常用；索引细切、搜索粗切是经典搭配 |
| smartcn 等 | 视发行版与插件而定 |
| 拼音插件 | 支持拼音搜索（`redis` 搜「Redis」书） |

推荐搭配：

- **索引**：`ik_max_word`（尽量多切，提高召回）
- **搜索**：`ik_smart`（粗切，减少噪音）

索引与查询 Analyzer 不一致是「明明有数据却搜不到」的第一怀疑点。

### 7.4 Mapping 一旦定错

字段类型错误（如把该 `keyword` 的做成了 `text`）后，往往需要：

1. 建新索引（正确 Mapping）
2. Reindex 数据
3. 用 Alias 原子切换

这也是别名（Alias）在生产中几乎必用的原因。

---

## 8. 文档 CRUD

以下为 REST 风格示意（版本细节以你安装的 ES 为准）。

### 8.1 创建 / 全量覆盖

```http
PUT /books/_doc/10086
Content-Type: application/json

{ "bookId": 10086, "title": "Redis 设计与实现", "status": 1 }
```

### 8.2 部分更新

```http
POST /books/_update/10086
Content-Type: application/json

{ "doc": { "saleStock": 10 } }
```

### 8.3 删除

```http
DELETE /books/_doc/10086
```

### 8.4 批量 Bulk

同步书城全量 / 增量时几乎必用 Bulk，减少 HTTP 往返：

```http
POST /_bulk
Content-Type: application/x-ndjson

{ "index": { "_index": "books", "_id": "10086" } }
{ "bookId": 10086, "title": "Redis 设计与实现", "status": 1 }
{ "delete": { "_index": "books", "_id": "10087" } }
```

注意：Bulk 是「尽可能多成功」；单条失败要检查 `items` 里的 `error`，不能只看 HTTP 200。

---

## 9. Query DSL 精要

### 9.1 查询上下文 vs 过滤上下文

| 上下文 | 典型子句 | 是否打分 | 缓存 |
| --- | --- | --- | --- |
| Query | `match`、`multi_match` | 要相关度分 | 不强调 |
| Filter | `term`、`range`、`bool.filter` | 不计分（或常数） | 可被缓存，更快 |

书城实践：**关键词用 query，分类 / 状态 / 库存范围用 filter。**

### 9.2 `term` vs `match`

- `term`：不分词（对 `keyword` 或数值），精确匹配  
- `match`：先分析查询串再匹配 `text` 字段  

错误示例：对分词后的 `text` 字段用 `term` 查整句中文，经常查不到。

### 9.3 Bool 组合（最常用）

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "Redis 实战",
            "fields": ["title^3", "author^2", "description"]
          }
        }
      ],
      "filter": [
        { "term": { "status": 1 } },
        { "term": { "categoryId": 3 } },
        { "range": { "saleStock": { "gte": 1 } } }
      ],
      "must_not": [
        { "term": { "bookId": 0 } }
      ]
    }
  },
  "from": 0,
  "size": 20,
  "highlight": {
    "fields": { "title": {}, "description": {} }
  }
}
```

| 子句 | 含义 |
| --- | --- |
| `must` | 必须匹配，贡献得分 |
| `filter` | 必须匹配，不贡献得分 |
| `should` | 可选匹配，提升得分（可设 `minimum_should_match`） |
| `must_not` | 必须不匹配 |

`title^3` 表示标题字段权重 ×3，书名命中会排更前。

### 9.4 其它常用查询

| 查询 | 用途 |
| --- | --- |
| `match_phrase` | 短语匹配（词序、邻近） |
| `prefix` / `wildcard` | 前缀、通配（慎用，易慢） |
| `fuzzy` | 容错（拼写错误） |
| `ids` | 按 `_id` 批量取 |
| `exists` | 字段存在 |
| `nested` | 嵌套对象查询（评论列表等） |

### 9.5 分页

| 方式 | 适用 |
| --- | --- |
| `from` + `size` | 浅分页（如前几页）；`from+size` 有上限（默认相关限制需关注） |
| `search_after` | 深度翻页、无限流式向后翻 |
| Scroll | 快照式遍历（适合导出，不适合实时用户翻页） |

书城列表 UI 一般浅分页即可；运营导出用 Scroll / `search_after`。

---

## 10. 相关性与排序

### 10.1 默认相关度

现代 ES 默认常用 **BM25**（词频、逆文档频率、字段长度归一等）。面试能说到：

- 稀有词命中权重更高
- 字段越短，同样命中往往分更高
- 可通过字段 boost、`function_score` 调业务分

### 10.2 业务排序叠加

书城常见「相关度 + 业务」：

```json
{
  "query": {
    "function_score": {
      "query": { "match": { "title": "Redis" } },
      "functions": [
        {
          "field_value_factor": {
            "field": "saleStock",
            "factor": 0.1,
            "modifier": "log1p"
          }
        }
      ],
      "boost_mode": "sum"
    }
  }
}
```

或简单粗暴：先 filter 出候选，再 `sort`: `[{ "_score": "desc" }, { "updatedAt": "desc" }]`。

注意：**库存、价格等强一致字段**若只活在 ES，会有同步延迟；展示前也可回源 MySQL 校准（类似本仓库推荐链路「召回后再回查库存」）。

---

## 11. 聚合 Aggregation

聚合用于「搜索结果上的统计 / 分面」，不是替代数仓。

示例：当前搜索条件下，按分类计数：

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [{ "match": { "title": "Java" } }],
      "filter": [{ "term": { "status": 1 } }]
    }
  },
  "aggs": {
    "by_category": {
      "terms": { "field": "categoryId", "size": 20 }
    }
  }
}
```

常见类型：

- `terms`：分桶计数  
- `range` / `histogram`：区间  
- `avg` / `sum` / `max`：指标  
- 子聚合：桶套指标  

书城用例：搜索页左侧「分类筛选」facet。

---

## 12. 与 MySQL 的数据同步

ES 不是事务库，同步策略决定「搜到的是不是刚改的数据」。

### 12.1 三种主流方案

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| 双写 | 写 MySQL 成功后再写 ES | 实现直观 | 要处理部分失败、重试、顺序 |
| 订阅 Binlog | Canal / Debezium 等监听后写 ES | 与业务解耦 | 运维链路长，要处理乱序与回溯 |
| 定时全量 / 增量 | 按 `updated_at` 扫表 Bulk | 简单可靠 | 延迟大，压力周期性 |

书城体量小：可先 **应用双写 + 失败进补偿表/MQ**；做大了再上 Binlog。

### 12.2 双写时序建议

```text
创建图书:
  1) 事务内写 MySQL
  2) 提交成功后异步发「索引消息」或同步写 ES
  3) 失败 → 重试队列 / 定时对账任务补齐

更新库存（高频）:
  - 若搜索不强依赖实时库存：可降频同步，或搜索只存「是否有货」布尔
  - 详情页库存仍以 MySQL / Redis 为准
```

与本仓库缓存哲学一致：**搜索索引可短暂旧，交易真相不能旧。**

### 12.3 删除与下架

- 业务下架：`status=0` 后 **update 文档**（保留文档便于运营搜到）或 **delete 文档**
- 物理删除：同步 `DELETE`，避免搜到幽灵书
- 与 Bloom 类似：删除策略要在「可搜到下架书」与「绝对搜不到」之间产品决策

### 12.4 对账

定期任务：

1. 抽样对比 MySQL `bookId` 集合与 ES `count`
2. 对比关键字段 hash（title、status）
3. 不一致则重索引

没有对账的双写，线上终究会漂。

---

## 13. Spring Boot 集成要点

### 13.1 客户端选型（概念层）

| 方式 | 说明 |
| --- | --- |
| 官方 Java API Client | 新项目优先了解 |
| ElasticsearchClient / RestClient | 直接打 REST |
| Spring Data Elasticsearch | Repository 风格，适合简单 CRUD + 派生查询 |

注意版本：Spring Boot、Spring Data ES、ES 服务器大版本必须对齐，乱配是联调第一坑。

### 13.2 分层建议（贴合本仓库四层）

```text
BookController（或 SearchController）
  → BookSearchService
      → 调 ES（关键词检索、高亮）
      → 可选：用 id 列表回查 BookCatalogService / Redis 详情（补库存、架位）
  → 返回统一 ApiResponse
```

AI 侧：`BookSearchTool` 可改为调 `BookSearchService`（内部走 ES），仍保持「事实走 Tool / Service」。

### 13.3 配置与安全

- 生产关闭公网裸露 `9200`
- 开 HTTPS / 认证（Elastic 内建安全或前置网关）
- 连接池、超时、失败降级：ES 挂时列表可降级回 MySQL `LIKE` 或返回「搜索繁忙」

---

## 14. 书城落地设计草案

以下为「若本仓库引入 ES」的推荐最小闭环，便于面试口述，不必立刻编码。

### 14.1 索引别名

- 物理索引：`books_v1`、`books_v2`
- 别名：`books_read`（查询）、`books_write`（写入）
- Mapping 变更：reindex 到 `books_v2` → 切换别名

### 14.2 读写路径

```text
读者搜索 keyword
  → ES：bool(must multi_match, filter status=1, category?)
  → 得到 bookId 列表 + 高亮
  → （可选）Redis/MySQL 补全实时库存与架位
  → 返回 PageResult

管理端改书 / 上架
  → MySQL 提交
  → 异步 Upsert ES 文档
  → 失败进补偿
```

### 14.3 与现有缓存共存

| 组件 | 职责 |
| --- | --- |
| ES | 搜索候选集、相关度、高亮、facet |
| Redis 详情缓存 | `bookId` → 详情热读（现有 Cache-Aside） |
| Bloom | 防穿透（详情 ID 探测，不是搜索主路径） |
| MySQL | 真相源 |

搜索结果 **不要** 整页长期缓死而不失效；可对「热门关键词」短 TTL 缓存 id 列表。

### 14.4 最小字段集

首期只索引：`bookId, title, author, categoryId, description, status, updatedAt`。  
库存字段可后加，或仅加 `inStock: boolean` 降低更新频率。

---

## 15. ES vs 本仓库其它检索

| 手段 | 解决什么 | 本仓库 |
| --- | --- | --- |
| MySQL `LIKE` | 小数据量标题模糊 | **现状** |
| Elasticsearch | 全文相关度、多字段、聚合 | **未引入（规划可选）** |
| Milvus + Embedding | 语义相似（「想学后端看什么」） | AI RAG / 推荐语义召回 |
| 规则 / SQL 推荐 | 分类、热门、共现 | `BookRecommendService` |

三者不是互斥：

```text
用户输入
  ├─ 明确书名 / ISBN     → ES 或 SQL 精确/全文
  ├─ 模糊主题语义        → Milvus
  └─ 「我的借阅」类事实  → Tool + MySQL（绝不靠向量瞎编）
```

口诀：**关键词检索偏 ES，语义相似偏向量，事务事实偏 MySQL。**

---

## 16. 运维与常见坑

### 16.1 常见坑

| 坑 | 现象 | 处理 |
| --- | --- | --- |
| 分词不一致 | 有数据搜不到 | 统一 index/search analyzer；用 `_analyze` API 调试 |
| `text` 上 `term` | 查不到 | 改用 `match`，或查 `.keyword` |
| 深度 `from` | 慢、占内存 | `search_after` |
| 主分片过多 / 过少 | 小索引碎一地或太大难平衡 | 按数据量规划，常见从 1～3 起步 |
| 把 ES 当主键库 | 丢数据、难事务 | 仅搜索分析 |
| Bulk 当成功 | 部分 item 失败 | 检查响应 `errors` |
| Mapping 后改类型 | 拒绝或需 reindex | 别名 + 重建 |
| 堆外 / 磁盘打满 | 集群变红、只读 | 监控水位、删旧索引 |

### 16.2 本地学习环境

```bash
# 示例：官方 Elastic 或社区镜像，注意版本与安全默认账号
docker run -d --name es01 \
  -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.0
```

中文分词需自行装 IK 等插件（版本与 ES 对齐）。学习阶段也可用单节点；理解分片概念即可，不必一上来三节点。

### 16.3 必会调试 API

```http
GET /books/_mapping
GET /books/_search
POST /_analyze
{ "analyzer": "ik_smart", "text": "Redis设计与实现" }
GET /_cluster/health
GET /_cat/indices?v
```

`_analyze` 是分词问题的「X 光」。

---

## 17. 分阶段学习路线

### E1：概念与手敲（2～3 天）

- 搞清 Index / Document / Shard / Replica / Mapping
- 本地起 ES，用 Kibana Dev Tools 或 curl 完成 CRUD
- 画一张「写入 → refresh → 可搜」时序

产出：自己的名词表 + 一次成功的 index/search。

### E2：中文检索（3～5 天）

- 安装 IK，对比 `ik_max_word` / `ik_smart` 的 `_analyze` 结果
- 为「模拟图书」建显式 Mapping
- 写出 `bool + multi_match + filter` 查询与高亮

产出：10 条书目数据 + 5 个查询用例（含「搜得到 / 被 status 过滤」）。

### E3：同步与一致性（3～5 天）

- 设计 MySQL ↔ ES 双写伪代码（含失败重试）
- 说明库存字段是否进 ES、如何降级
- 写一页「对账」方案

产出：同步时序图（可放进个人笔记）。

### E4：对标本仓库（可选 1 周）

- 对照 `BookRepository` 的 `LIKE`，列出 ES 可增强点
- 口述：AI Tool、Redis、Milvus、ES 的边界
- 若实现：只做「搜索接口走 ES，详情仍走现有缓存」的最小 PR 级设计

产出：一页「智慧书城搜索演进」说明（能讲 5 分钟）。

### E5：进阶（按需）

- `search_after`、索引生命周期（ILM）
- `function_score` 业务加权
- 集群脑裂认知、副本与法定人数（了解级）
- 与日志场景（ELK）区分：搜索业务索引 vs 日志索引

---

## 18. 能力自检与面试口述

### 18.1 自检清单

- [ ] 能解释倒排索引，并说明分词错误会导致什么
- [ ] 能区分 `term` / `match` / `filter`
- [ ] 能写出书城风格的 `bool` 查询
- [ ] 能说明 refresh 与「近实时」
- [ ] 能对比双写 / Binlog / 定时同步
- [ ] 能说清本仓库为何现在不用 ES，以及上线信号
- [ ] 能区分 ES（关键词）与 Milvus（语义）

### 18.2 推荐口述稿（约 2 分钟）

> 我们书城目前书目量不大，标题搜索用 MySQL `LIKE` 能满足。如果以后要做多字段全文、相关度排序和高亮，我会引入 Elasticsearch：MySQL 继续做事务与库存真相，ES 只承担搜索。索引里放书名、作者、简介、分类和上架状态；写入走「MySQL 提交后异步 Upsert ES + 失败补偿 + 定期对账」。查询用 `multi_match` 做关键词，`filter` 过滤上架和分类。AI 客服的查书 Tool 仍调业务检索服务，语义推荐继续用向量库，三者分工是：关键词 ES、语义 Milvus、事实 MySQL。

### 18.3 可能被追问

1. ES 能替代数据库吗？ → 不能当事务真相源。  
2. 搜不到刚上架的书？ → refresh 间隔、同步延迟、分词、status filter。  
3. 和 Redis 搜索（如 RediSearch）比？ → 生态、聚合、运维与团队熟悉度选型。  
4. 深度分页怎么做？ → `search_after`，避免大 `from`。  
5. 字段要改类型？ → 新索引 + reindex + 别名切换。

### 18.4 自检参考答案

下列对应 18.1 清单，可直接用于默写与面试口述。

---

#### （1）倒排索引是什么？分词错误会导致什么？

**倒排索引**：不是「文档 → 词」，而是 **「词 → 含有该词的文档列表」**（再附带位置、频率等）。

```text
正排：文档1 → [redis, 设计, 实现]
倒排：redis → [文档1, 文档2]
      设计 → [文档1]
```

搜索时：把查询串也切成词 → 查倒排表 → 合并 posting → 打分取 Top-K。

**分词错误会导致：**

| 错误类型 | 后果 |
| --- | --- |
| 索引时切错 / 过粗 | 该有的词根本没进倒排 → **有书搜不到** |
| 查询时切错 / 与索引不一致 | 查的词和索引里的词对不上 → **明明有数据却 0 命中** |
| 切太碎、噪音多 | 召回一堆不太相关的结果 → **准度差** |
| 中文未装 IK、用默认分析器 | 常按单字或不当规则切 → 中文书名检索惨不忍睹 |

调试用 `_analyze` API，对比「索引 analyzer」与「搜索 analyzer」输出是否一致。

---

#### （2）区分 `term` / `match` / `filter`

| | `term` | `match` | `filter`（上下文） |
| --- | --- | --- | --- |
| 是否分词 | **不分**（整值比） | **会**走 analyzer | 取决于内部子句；常用 `term`/`range` |
| 典型字段 | `keyword`、数字、状态码 | `text`（标题、简介） | `status`、`categoryId`、库存区间 |
| 是否打分 | 在 query 里可打分 | 打相关度分 | **不计分**（或常数），可缓存，更快 |
| 书城例子 | `term: { status: 1 }` | `match: { title: "Redis 实战" }` | 放在 `bool.filter` 里过滤上架 |

易错：对已分词的 `text` 用 `term` 查整句中文 → 经常查不到；精确过滤用 `.keyword` 或数值字段上的 `term`。

---

#### （3）书城风格的 `bool` 查询

需求：关键词搜书名/作者/简介，只看上架，可按分类筛，按相关度（标题加权）排序，带高亮。

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "Redis 实战",
            "fields": ["title^3", "author^2", "description"]
          }
        }
      ],
      "filter": [
        { "term": { "status": 1 } },
        { "term": { "categoryId": 3 } },
        { "range": { "saleStock": { "gte": 1 } } }
      ]
    }
  },
  "from": 0,
  "size": 20,
  "highlight": {
    "fields": {
      "title": {},
      "description": {}
    }
  }
}
```

口述要点：`must` 管「像不像」并打分；`filter` 管「合不合法」不计分；库存若强一致，展示前可再回源 MySQL/Redis（与本仓库推荐链路一致）。

---

#### （4）`refresh` 与「近实时」

```text
写入文档 → 先到内存 buffer（+ translog）
         → refresh（默认约 1 秒）→ 变成可被搜索的段
         → flush → 更持久地落盘
```

- **近实时（NRT）**：不是写完立刻全局可搜，通常 **约 1 秒级** 可见（还可叠加「MySQL→ES 同步延迟」）。
- **`refresh`**：让近期写入变为 **可搜索**；不等于每次写入都立刻 refresh。
- 业务含义：库存/订单真相仍看 MySQL；搜索允许短暂旧。演示「写下立刻搜到」可用 `refresh=true`，勿滥用。

---

#### （5）双写 / Binlog / 定时同步对比

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **应用双写** | 写 MySQL 成功后再写 ES / 发 MQ | 实现直观、延迟相对低 | 易漏写路径；DB 成功 MQ 失败要补偿；多服务难统一 |
| **Binlog / CDC** | Canal/Debezium 听变更再投 MQ / 写 ES | 业务只写库；覆盖脚本与多服务写 | 运维重；最终一致；要幂等与位点 |
| **定时同步** | 按 `updated_at` 扫表 Bulk | 简单、易对账 | 延迟大；周期性扫表压力 |

书城现阶段写入口集中：双写或「写库后发 MQ」够用；多服务改书目或上 ES 后，Binlog 更划算；无论哪种都要 **失败重试 + 定期对账**。详见 [Spring Boot 使用 Binlog 学习文档](./SpringBoot-Binlog学习文档.md)。

---

#### （6）本仓库为何现在不用 ES？上线信号是什么？

**现在不用的理由（与规划一致）：**

- 书目规模不大，`BookRepository` 对 `title` 做 `LIKE` 已能支撑列表搜索
- ES 带来：集群运维、Mapping/分词、同步与对账成本
- AI 侧语义召回已有 **Milvus**；精确事实走 **Tool + MySQL**，不靠再堆一个搜索中间件

**可以考虑上线的信号：**

- 要搜 **标题 + 作者 + 简介**，并按 **相关度** 排序、高亮
- 模糊查询拖慢 MySQL，或检索 QPS 明显上升
- 需要 facet（按分类聚合）、拼音/纠错/联想
- 产品明确「搜索体验」为优先级，且愿意维护同步链路

上线后分工不变：**MySQL 真相，ES 只搜索，Redis 热读详情。**

---

#### （7）ES（关键词）与 Milvus（语义）

| | **Elasticsearch** | **Milvus** |
| --- | --- | --- |
| 匹配依据 | 分词后的 **词项**、倒排、BM25 等 | **向量距离**（Embedding 相似） |
| 擅长 | 「Redis」「Java 并发」等关键词、过滤、聚合 | 「想学后端看什么」等模糊意图 |
| 书城落点 | 规划中的全文检索（现状 `LIKE`） | AI RAG / 语义推荐召回 |
| 不擅长 | 纯语义「意思接近但用词完全不同」 | ISBN/架位/库存等精确事实 |

```text
明确书名 / 关键词  → ES（或现在的 SQL LIKE）
模糊主题 / 相似书  → Milvus
我的借阅 / 库存    → Tool + MySQL（绝不靠向量编造）
```

口诀：**关键词找词用 ES，意思像不像用向量，对不对用 MySQL。**

---

## 相关文档

- [加深方向学习路线](./README.md)
- [缓存](./缓存.md)
- [书城系统功能规划](../书城系统功能规划.md)（「暂不建议做」中的 ES）
- [向量检索策略与存储单元](../向量检索策略与存储单元.md)
- [AI 模块学习文档](../AI模块学习文档.md)
- [Redis 缓存查询三种方案学习文档](../Redis缓存查询三种方案学习文档.md)
- [Spring Boot 使用 Binlog 学习文档](./SpringBoot-Binlog学习文档.md)

---

## 一句话总结

**Elasticsearch 用倒排索引解决「找得到、排得准」；智慧书城现阶段 MySQL `LIKE` 够用，学 ES 是为演进全文检索与面试深度——记住 MySQL 管真相、ES 管搜索、Milvus 管语义、Redis 管热读。**
