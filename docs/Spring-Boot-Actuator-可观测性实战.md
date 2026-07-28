# Spring Boot Actuator 与可观测性实战：从健康检查到 TraceId


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联**：`management.endpoints` — Actuator 与指标  
> **索引**：[学习文档中心](./README.md)

线上出问题时，最怕两件事：一是不知道服务还健不健康，二是日志里找不到同一条请求的完整链路。Spring Boot Actuator、Micrometer 与 MDC 恰好补上这两块缺口。本文用一套可落地的配置，把健康检查、指标导出和请求追踪串起来。

---

## 为什么需要可观测性

应用一旦部署到多实例环境，仅靠「进程还在」远远不够。数据库连接池打满、Redis 抖动、消息中间件不可达，都可能让接口大面积失败，而进程本身仍在运行。

可观测性通常拆成三块：

| 维度 | 回答的问题 | 常见手段 |
| --- | --- | --- |
| 健康检查 | 现在能不能接流量 | Actuator `/health` |
| 指标 | 哪里变慢、哪里变满 | Micrometer + Prometheus |
| 日志追踪 | 这次请求经过了哪些节点 | MDC 中的 `traceId` |

下面按接入顺序实战。

---

## 一、引入 Actuator

Maven：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Gradle：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

启动后默认会暴露少量端点。生产环境不要一上来把所有端点全部放开，先从 `health`、`info`、`metrics` 开始。

---

## 二、暴露核心端点

`application.yml` 示例：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized   # 开发可改为 always
  info:
    env:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

常用端点说明：

| 端点 | 用途 |
| --- | --- |
| `/actuator/health` | 聚合健康状态，供 K8s / 负载均衡探活 |
| `/actuator/info` | 应用元信息（版本、构建号等） |
| `/actuator/metrics` | 指标目录与单个指标查询 |
| `/actuator/prometheus` | Prometheus 抓取格式（需额外依赖） |

安全建议：

1. 生产环境用 Spring Security 限制 `/actuator/**`
2. 探活只暴露 `health`，指标走内网或独立端口
3. `show-details` 勿对公网匿名返回依赖细节

可用独立管理端口，与业务端口隔离：

```yaml
management:
  server:
    port: 8081
```

---

## 三、自定义 Health Indicator

Actuator 对 DataSource、Redis、RabbitMQ 等常见中间件通常自带检查。一旦依赖不在默认列表里，或需要更细的业务判定，就要写自定义 Indicator。

### 1. 通用自定义示例

以「外部向量检索服务」为例（也可换成任意 HTTP / gRPC 依赖）：

```java
@Component
public class VectorStoreHealthIndicator implements HealthIndicator {

    private final VectorStoreClient client;

    public VectorStoreHealthIndicator(VectorStoreClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            long latencyMs = client.ping();
            return Health.up()
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("reason", "vector store unreachable")
                    .build();
        }
    }
}
```

### 2. Redis 业务级检查

默认 Redis Health 只能证明「能连上」。若还要确认关键 Key 可读：

```java
@Component
public class RedisBizHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redis;

    public RedisBizHealthIndicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Health health() {
        try {
            String pong = redis.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equalsIgnoreCase(pong)
                    ? Health.up().withDetail("ping", pong).build()
                    : Health.down().withDetail("ping", pong).build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
```

### 3. 健康分组：存活与就绪

K8s 场景建议拆开：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
    group:
      readiness:
        include: readinessState,db,redis,rabbit
      liveness:
        include: livenessState
```

- **liveness**：进程是否该被重启（尽量别绑外部依赖）
- **readiness**：是否可以接流量（可绑 DB / Redis / MQ）

访问示例：

- `/actuator/health/liveness`
- `/actuator/health/readiness`

---

## 四、Micrometer + Prometheus 导出指标

### 1. 依赖

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

引入后可访问 `/actuator/prometheus`。

### 2. 自动能拿到什么

在典型 Web + JDBC 应用中，常见指标包括：

- JVM：堆内存、GC、线程数
- HTTP：请求耗时、状态码分布
- 连接池：活跃连接、等待线程（如 HikariCP）
- 自定义 Timer / Counter

HikariCP 示例（确认连接池注册到 Micrometer）：

```yaml
spring:
  datasource:
    hikari:
      pool-name: main-pool
      maximum-pool-size: 20
```

### 3. 业务自定义指标

```java
@Service
public class OrderMetrics {

    private final Counter paidCounter;
    private final Timer payTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.paidCounter = Counter.builder("order.paid.count")
                .description("成功支付订单数")
                .register(registry);
        this.payTimer = Timer.builder("order.pay.latency")
                .description("支付耗时")
                .register(registry);
    }

    public void recordPaid(Runnable payAction) {
        payTimer.record(payAction);
        paidCounter.increment();
    }
}
```

### 4. Prometheus 抓取配置

```yaml
scrape_configs:
  - job_name: spring-apps
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['app-1:8081', 'app-2:8081']
```

Grafana 可直接用 JVM / Spring Boot 官方 Dashboard，再叠加业务面板（支付成功率、队列积压、连接池使用率）。

---

## 五、日志接入 TraceId（MDC）

指标告诉你「系统慢了」，日志告诉你「这一次请求慢在哪」。用 MDC（Mapped Diagnostic Context）把 `traceId` 写入每条日志，是成本最低的链路串联方式。

### 1. 过滤器生成 / 透传 TraceId

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

### 2. Logback 模式带上 TraceId

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId}] - %msg%n</pattern>
```

日志效果：

```text
2026-07-20 22:01:12.331 [http-nio-8080-exec-3] INFO  c.e.order.PayService [traceId=a1b2c3d4e5] - pay start
2026-07-20 22:01:12.410 [http-nio-8080-exec-3] INFO  c.e.order.PayService [traceId=a1b2c3d4e5] - pay success
```

排查时用 `traceId` 在日志平台检索，即可还原单次请求上下文。

### 3. 异步与线程池注意点

线程切换会丢掉 MDC。提交异步任务前要手动传递：

```java
Map<String, String> context = MDC.getCopyOfContextMap();
executor.execute(() -> {
    if (context != null) {
        MDC.setContextMap(context);
    }
    try {
        // 业务逻辑
    } finally {
        MDC.clear();
    }
});
```

若已使用 Micrometer Tracing / OpenTelemetry，可进一步与 Zipkin、Jaeger 对接；MDC `traceId` 仍是日志侧的最小可用方案。

---

## 六、落地检查清单

接入完成后，建议按下面清单自测：

1. `/actuator/health` 返回 `UP`，人为停 Redis 后 readiness 变为 `DOWN`
2. `/actuator/info` 能看到构建版本或 Git 提交
3. `/actuator/prometheus` 能看到 `jvm_memory_used_bytes`、`http_server_requests_seconds`
4. 发一笔请求，日志中出现统一 `traceId`，响应头带回同一值
5. Actuator 端口或路径已做鉴权 / 网络隔离

---

## 七、常见坑

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| `health` 一直 `DOWN` | 某个弱依赖被算进默认聚合 | 用 health group，或把非关键依赖标为非必需 |
| Prometheus 抓不到 | 未加 `micrometer-registry-prometheus` 或未暴露端点 | 检查依赖与 `exposure.include` |
| 日志没有 `traceId` | pattern 未写 `%X{traceId}`，或异步丢了 MDC | 改 pattern，线程池透传 MDC |
| 探活误杀实例 | liveness 绑了外部依赖 | liveness 只看进程自身 |

---

## 小结

1. **Actuator** 解决「能不能接流量」
2. **Micrometer + Prometheus** 解决「哪里在变差」
3. **MDC TraceId** 解决「这一次请求怎么串起来」

三者不必一次上齐全家桶：先暴露 `health`，再导出 `prometheus`，最后补 `traceId`，通常一周内就能把线上可观测性从「盲飞」拉到「可定位」。后续若流量与团队规模上来，再引入完整 Tracing 与统一告警规则即可。
