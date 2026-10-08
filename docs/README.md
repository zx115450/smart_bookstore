# 智慧书城 · 学习文档中心

本目录是 **smart_bookstore** 的配套学习资料：每篇文档尽量绑定仓库中的真实模块与代码路径，方便你对照阅读、跟练、面试口述。

> 快速开始请先看仓库根目录 [README.md](../README.md)；环境用 `docker compose up -d` 一键拉起中间件。

---

## 怎么学（推荐路径）

| 阶段 | 目标 | 入口 |
| --- | --- | --- |
| 0. 认识项目 | 业务闭环、技术栈、包结构 | [项目介绍](./项目介绍.md) → [项目流程图](./项目流程图.md) → [项目评价](./项目评价.md) |
| 1. 跑起来 | 本地配置、联调、Flyway | [前后端联调文档](./前后端联调文档.md) · [Flyway 落地指南](./Flyway落地指南.md) · [接口文档](./接口文档.md) |
| 2. 认证与安全 | 登录、双 Token、OAuth | 见下方「认证」 |
| 3. 书城核心 | 分层、借阅、缓存、交易 | 见下方「书城与缓存」 |
| 4. 预约与签到 | 乐观锁、行锁、BitMap | 见下方「预约与签到」 |
| 5. 秒杀与 MQ | Lua、限流、死信 | 见下方「秒杀与消息」 |
| 6. AI 客服 | Tool / RAG / Memory | [AI 模块学习文档](./AI模块学习文档.md) |
| 7. 阅读笔记 Agent | 试看 + 笔记 + 总结归纳 | [AI 阅读笔记模块分步实现指南](./AI阅读笔记模块分步实现指南.md) |
| 7b. 阅读板块开工清单 | 目录 + BFF 读章 + Agent（可 Mock 媒资） | [线上阅读与学习 Agent 板块 · 阅读说明](./线上阅读与学习Agent板块/00-阅读说明.md)（B0～B6；总纲见 [分步实现指南](./线上阅读与学习Agent板块分步实现指南.md)） |
| 8. 与媒资双仓协同 | 线上书 / 笔记 / AI / 视频媒资平台 | 工作区 [双项目协同实现方案](../../docs/双项目协同-媒资平台与智慧书城实现方案.md) |
| 9. 加深 | 高并发 / 缓存 / MQ / AI 工程 | [learning/](./learning/README.md) · [后续学习路线](./后续学习路线.md) |

---

## 按业务域学习

### 认证与登录（`com.zx.auth`）

| 文档 | 关联要点 |
| --- | --- |
| [登录鉴权实现流程](./登录鉴权实现流程.md) | **总览**：登录 → Filter 鉴权 → 刷新 → 登出 |
| [登录流程学习文档](./登录流程学习文档.md) | `AuthController` / `AuthService` / Handler 工厂 |
| [双 Token 登录学习文档](./双Token登录学习文档.md) | Access + Refresh、会话轮换 |
| [JWT-Refresh-Token 完整链路](./JWT-Refresh-Token完整链路.md) | 端到端时序与表结构 |
| [工厂模式学习文档](./工厂模式学习文档.md) | 登录 / 发码 / 优惠券计算器扩展点 |
| [QQ 扫码登录学习文档](./QQ扫码登录学习文档.md) | OAuth 授权码、身份绑定 |
| [QQ 扫码登录流程图](./QQ扫码登录流程图.md) | 时序图 |

### 架构与工程基础

| 文档 | 关联要点 |
| --- | --- |
| [四层架构学习文档](./四层架构学习文档.md) | Controller → Service → Repository → Mapper |
| [IDEA 使用 Git 教程学习文档](./IDEA使用Git教程学习文档.md) | 工程基础（弱耦合） |
| [Java 反射学习文档](./Java反射学习文档.md) | 框架间接使用反射 |
| [计算机 I/O 模型介绍学习文档](./计算机IO模型介绍学习文档.md) | BIO / NIO 背景；对接 Netty 路线 |
| [前后端必备计算机网络知识](./前后端必备计算机网络知识.md) | HTTP / 跨域 / Cookie 等联调基础 |
| [BFF 架构实战指南](./BFF架构实战指南.md) | 前后端边界与聚合层思路 |
| [learning/Oracle 使用文档](./learning/Oracle使用文档.md) | 实例/物理文件、用户表空间、过程触发器、客户端（通用基础） |
| [learning/Java 使用 Oracle 文档](./learning/Java使用Oracle文档.md) | JDBC / Spring Boot / MyBatis 连接 Oracle，与 MySQL 差异 |
| [learning/MySQL 与 Oracle 保证 CP 面试文档](./learning/MySQL与Oracle保证CP面试文档.md) | CAP、MySQL 半同步/GR、Oracle RAC/Data Guard、高频题与口述 |

### 书城、缓存与一致性（`com.zx.bookstore`）

| 文档 | 关联要点 |
| --- | --- |
| [书城系统功能规划](./书城系统功能规划.md) | 业务范围 |
| [书城系统分阶段实施指南](./书城系统分阶段实施指南.md) | 分期落地 |
| [智慧书城分步实现指南](./智慧书城分步实现指南.md) | 总实施路线 |
| [Redis 缓存查询三种方案学习文档](./Redis缓存查询三种方案学习文档.md) | Cache Aside / 布隆 / 逻辑过期 |
| [learning/Redis 动态拓扑接入学习文档](./learning/Redis动态拓扑接入学习文档.md) | 单体 / 主从 / 哨兵 / 分片集群：`bookstore.redis.mode` |
| [learning/Elasticsearch 学习文档](./learning/Elasticsearch学习文档.md) | 全文检索加深；现状为 MySQL `LIKE`，规划可选演进 |
| [learning/Spring Boot 使用 Binlog 学习文档](./learning/SpringBoot-Binlog学习文档.md) | CDC：Canal / Debezium → Spring Boot 删缓存 / 同步搜索 |
| [生产可用布隆过滤器学习文档](./生产可用布隆过滤器学习文档.md) | Bitmap 布隆防穿透 |
| [生产可用逻辑过期缓存学习文档](./生产可用逻辑过期缓存学习文档.md) | 互斥重建热 Key |
| [Redis 布隆过滤器 Bitmap 实现原理](./Redis布隆过滤器Bitmap实现原理.md) | 原理补充 |
| [数据库与缓存高一致性业务场景实现方案](./数据库与缓存高一致性业务场景实现方案.md) | 一致性格局 |
| [Redis-ZSET-Lua 借阅逾期精准触发学习文档](./Redis-ZSET-Lua借阅逾期精准触发学习文档.md) | 到期调度 |
| [借阅逾期四种实现思路对比](./借阅逾期四种实现思路对比.md) | 方案选型 |
| [定时任务频率与延迟轮询选型指南](./定时任务频率与延迟轮询选型指南.md) | Scheduler vs 延迟队列 |

### 预约与签到（`com.zx.reservation` / `checkin`）

| 文档 | 关联要点 |
| --- | --- |
| [自习室预约业务流程](./自习室预约业务流程.md) | 主流程 |
| [预约系统功能规划](./预约系统功能规划.md) | 能力边界 |
| [预约系统业务价值点](./预约系统业务价值点.md) | 简历 / 答辩话术 |
| [乐观锁学习文档](./乐观锁学习文档.md) | 时段名额 `@Version` |
| [幂等与行级锁学习文档](./幂等与行级锁学习文档.md) | idempotencyKey + FOR UPDATE |
| [InnoDB 锁机制学习文档](./InnoDB锁机制学习文档.md) | 行锁原理对照预约 / 借阅 |
| [MySQL 锁与业务场景学习文档](./MySQL锁与业务场景学习文档.md) | 锁选型 |
| [P2 签到业务流程与技术栈](./P2签到业务流程与技术栈.md) | BitMap 连续签到 |

### 秒杀与消息（`seckill` / RabbitMQ）

| 文档 | 关联要点 |
| --- | --- |
| [P4 秒杀业务流程与技术栈](./P4秒杀业务流程与技术栈.md) | Lua 扣减 + MQ 发券 |
| [生产可用令牌桶限流学习文档](./生产可用令牌桶限流学习文档.md) | grab 双维度限流 |
| [RabbitMQ 使用指南学习文档](./RabbitMQ使用指南学习文档.md) | 本仓库拓扑、配置、发消与重试实操 |
| [learning/秒杀令牌桶限流](./learning/秒杀令牌桶限流.md) | 代码级拆解 |
| [learning/消息队列](./learning/消息队列.md) | MQ 加深 |
| [learning/消息模型与投递语义](./learning/消息模型与投递语义.md) | 投递语义 |
| [learning/MQ 本地重试与 Spring Template](./learning/MQ本地重试与Spring-Template学习文档.md) | Retry 队列 / 本地重试 / `*Template` |

### AI 客服（`com.zx.ai`）

| 文档 | 关联要点 |
| --- | --- |
| [AI 模块学习文档](./AI模块学习文档.md) | **主入口**：心智模型 + 读码顺序 |
| [AI 客服技术方案](./AI客服技术方案.md) | 方案总览 |
| [AI 模块分板块实施流程](./AI模块分板块实施流程.md) | 分步实施 |
| [AI 客服全链路实现思路与方案学习文档](./AI客服全链路实现思路与方案学习文档.md) | 全链路串联 |
| [Spring AI 框架核心内容学习文档](learning/SpringAI框架核心内容学习文档.md) | ChatClient / Tool / VectorStore |
| [Spring AI 常用类学习文档](learning/SpringAI常用类学习文档.md) | 常用类职责、原理、Boot 加载机制 |
| [Coding Agent 学习路线](learning/CodingAgent学习路线.md) | 类 Claude Code：Agent Loop / 工具集 / Diff / 流式 / 沙箱 |
| [向量检索策略与存储单元](./向量检索策略与存储单元.md) | Embedding / Milvus |

### 数字阅读与笔记 Agent（`com.zx.reader` 规划）

| 文档 | 关联要点 |
| --- | --- |
| [AI 阅读笔记模块分步实现指南](./AI阅读笔记模块分步实现指南.md) | 电子书试看 + 笔记 + Study Agent + RAG，R0～R5（原文保留） |
| [线上阅读与学习 Agent 板块](./线上阅读与学习Agent板块/00-阅读说明.md) | **本仓开工清单 B0～B6**；总纲见 [分步实现指南](./线上阅读与学习Agent板块分步实现指南.md) |
| [借阅阅读与演示前端修改说明](./借阅阅读与演示前端修改说明.md) | 借阅库存、续借、阅读解锁、进度落库与演示页改动 |
| 工作区 [双项目协同实现方案](../../docs/双项目协同-媒资平台与智慧书城实现方案.md) | 与 `video` 的职责边界、端口避让、P0～P6；错误码用 51xx |

### 可观测性与测试

| 文档 | 关联要点 |
| --- | --- |
| [Spring Boot Actuator 可观测性实战](./Spring-Boot-Actuator-可观测性实战.md) | `/actuator` |
| [Micrometer-Prometheus-Grafana 分步指南](./Micrometer-Prometheus-Grafana-分步指南.md) | 指标导出 |
| [测试板块分步实现指南](./测试板块分步实现指南.md) | T0～T6 |
| [admin Apifox 集合说明](./admin-apifox-collection.md) | 管理端接口集合 |

### 前端与部署

| 文档 | 关联要点 |
| --- | --- |
| [前端部署指南](./前端部署指南.md) | 仓库内 `nginx-smart-bookstore/`（`8088`）+ 生产 Nginx |
| [前端生成提示词](./前端生成提示词.md) | 用 AI 生成前端工程的提示词 |
| [前后端联调文档](./前后端联调文档.md) | 端口、代理、OAuth 回调 |
| [智慧书城项目业务价值点](./智慧书城项目业务价值点.md) | 业务卖点 |

### 进阶路线

| 文档 | 说明 |
| --- | --- |
| [后续学习路线](./后续学习路线.md) | 项目结束后 3～6 个月计划 |
| [后端进阶路线 - 业务 / AI / 中间件 / Netty](./后端进阶路线-业务AI与中间件Netty.md) | 进阶分轨 |
| [加深方向学习路线](./learning/README.md) | 高并发 / 缓存 / MQ / AI 工程 / Coding Agent 分册 |

---

## 文档约定

每篇「学习文档」尽量包含：

1. **学习目标**：读完能回答什么、能在仓库找到什么  
2. **关联包 / 文件路径**：对照 `src/main/java/com/zx/...`  
3. **建议前置**：先读哪几篇  
4. **正文**：原理 + 本项目落地 + 常见坑  

原「博客」文稿已统一改名为 `*学习文档.md`，叙述改为面向学习者，并挂上本仓库代码锚点。

根目录相关：

- [README.md](../README.md)：快速开始、模块与 API 概览、Nginx 前端启动  
- [docker-compose.yml](../docker-compose.yml)：MySQL / Redis / RabbitMQ（可选 Milvus）  
- [nginx-smart-bookstore/](../nginx-smart-bookstore/README.md)：便携 Nginx 前端演示（`8088`）
