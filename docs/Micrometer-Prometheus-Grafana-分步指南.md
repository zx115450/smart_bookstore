# Micrometer + Prometheus + Grafana 分步指南


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联**：`management.metrics` — 指标到看板  
> **索引**：[学习文档中心](./README.md)

把应用指标从「打点」走到「看图告警」，最少只需三层：

| 组件 | 职责 |
| --- | --- |
| Micrometer | 在应用内定义、聚合指标，对接多种监控后端 |
| Prometheus | 定时拉取、存储时间序列 |
| Grafana | 把指标画成曲线、仪表盘、告警 |

本文按「应用 → 采集 → 可视化」顺序落地，可在本机用 Docker 跑通全链路。

---

## 整体架构

```text
┌─────────────────────┐
│  Spring Boot 应用    │
│  Micrometer 打点     │
│  /actuator/prometheus│
└──────────┬──────────┘
           │ HTTP scrape（默认 15s）
┌──────────▼──────────┐
│     Prometheus       │
│  存储 + PromQL 查询  │
└──────────┬──────────┘
           │ 数据源查询
┌──────────▼──────────┐
│       Grafana        │
│  Dashboard + Alert   │
└─────────────────────┘
```

建议端口（本机演示）：

| 服务 | 端口 |
| --- | --- |
| 业务应用 | `8080` |
| Actuator（可与业务同端口） | `8080` |
| Prometheus | `9090` |
| Grafana | `3000` |

---

## 第一步：应用侧接入 Micrometer

### 1.1 添加依赖

Maven：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Gradle：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### 1.2 暴露 Prometheus 端点

`application.yml`：

```yaml
spring:
  application:
    name: demo-order-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    tags:
      application: ${spring.application.name}
  observations:
    key-values:
      region: local
```

说明：

- `micrometer-registry-prometheus` 会注册 `/actuator/prometheus`
- `metrics.tags.application` 给所有指标打上应用名，方便多服务区分
- 生产环境应对 `/actuator/**` 做鉴权或内网隔离

### 1.3 验证导出是否成功

启动应用后访问：

```bash
curl http://localhost:8080/actuator/prometheus
```

应能看到类似内容：

```text
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{application="demo-order-service",area="heap",id="PS Eden Space",} 1.2E8
```

若返回 404：检查依赖是否引入、`exposure.include` 是否包含 `prometheus`。

### 1.4（可选）业务自定义指标

```java
@Service
public class OrderMetrics {

    private final Counter paidCounter;
    private final Timer payTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.paidCounter = Counter.builder("order_paid_total")
                .description("成功支付订单数")
                .tag("channel", "app")
                .register(registry);
        this.payTimer = Timer.builder("order_pay_seconds")
                .description("支付耗时")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordPaid(Runnable action) {
        payTimer.record(action);
        paidCounter.increment();
    }
}
```

命名建议：

- 用 `snake_case`，与 Prometheus 习惯一致
- Counter 用 `_total` 后缀
- Timer / 耗时用 `_seconds` 后缀
- 高基数标签（如 userId、orderId）不要打进指标

完成本步后：**指标已在应用内存中聚合，并可通过 HTTP 被拉取。**

---

## 第二步：部署 Prometheus 拉取与存储

### 2.1 编写抓取配置

新建 `prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: spring-boot-apps
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8080   # Docker 访问本机应用（Windows / macOS）
        labels:
          env: local
```

Linux 若 `host.docker.internal` 不可用，可改为宿主机 IP，或把应用与 Prometheus 放进同一 Docker 网络，用服务名作为 `targets`。

多实例示例：

```yaml
scrape_configs:
  - job_name: spring-boot-apps
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - app-1:8080
          - app-2:8080
        labels:
          env: prod
```

### 2.2 用 Docker 启动 Prometheus

```bash
docker run -d --name prometheus \
  -p 9090:9090 \
  -v /绝对路径/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

Windows PowerShell 示例：

```powershell
docker run -d --name prometheus `
  -p 9090:9090 `
  -v ${PWD}/prometheus.yml:/etc/prometheus/prometheus.yml `
  prom/prometheus
```

### 2.3 确认抓取成功

1. 打开 [http://localhost:9090](http://localhost:9090)
2. 进入 **Status → Targets**
3. 目标 `spring-boot-apps` 状态应为 **UP**

常见失败：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| Target DOWN | 网络不通或端口错 | 检查 `targets`、防火墙 |
| 404 | 路径不是 `/actuator/prometheus` | 改 `metrics_path` |
| 空指标 | 应用未启动或端点未暴露 | 先 `curl` 验证应用端 |

### 2.4 用 PromQL 做第一次查询

在 Prometheus → **Graph** 中试查：

```promql
# JVM 堆内存使用
jvm_memory_used_bytes{area="heap"}

# HTTP 请求速率（若已有流量）
rate(http_server_requests_seconds_count[1m])

# 自定义支付计数（若已打点）
rate(order_paid_total[1m])
```

完成本步后：**指标已进入时间序列库，可用 PromQL 查询。**

---

## 第三步：Grafana 可视化与告警

### 3.1 启动 Grafana

```bash
docker run -d --name grafana \
  -p 3000:3000 \
  grafana/grafana
```

浏览器打开 [http://localhost:3000](http://localhost:3000)：

- 默认账号：`admin`
- 默认密码：`admin`（首次登录会要求修改）

### 3.2 添加 Prometheus 数据源

1. **Connections → Data sources → Add data source**
2. 选择 **Prometheus**
3. URL 填写：
   - Grafana 与 Prometheus 都在 Docker：`http://prometheus:9090`（需同一网络）
   - 本机简单联调：`http://host.docker.internal:9090`
4. 点击 **Save & test**，应显示成功

同一 Compose 网络示例：

```yaml
services:
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
```

此时 Grafana 数据源 URL 用 `http://prometheus:9090`。

### 3.3 导入现成 JVM 面板（最快看到图）

1. 左侧 **Dashboards → New → Import**
2. 输入社区面板 ID（常用）：
   - `4701`：JVM (Micrometer)
   - `11378`：Spring Boot Statistics（视版本可用性而定）
3. 选择刚才的 Prometheus 数据源并导入

应能立刻看到堆内存、GC、线程等曲线。

### 3.4 手建一张业务面板

1. **Dashboards → New dashboard → Add visualization**
2. 数据源选 Prometheus
3. 查询示例：

```promql
sum(rate(http_server_requests_seconds_count{application="demo-order-service"}[1m])) by (uri, status)
```

4. 可视化类型选 **Time series**
5. 标题改为「HTTP QPS by URI」
6. **Save dashboard**

支付耗时 P99 示例：

```promql
histogram_quantile(
  0.99,
  sum(rate(order_pay_seconds_bucket[5m])) by (le)
)
```

### 3.5 配置一条基础告警

以「实例掉线」为例（Grafana Alerting）：

1. 编辑某面板 → **Alert → New alert rule**
2. 查询条件示例：

```promql
up{job="spring-boot-apps"}
```

3. 条件设为：当值 `< 1` 持续 `1m` 则触发
4. 配置通知渠道（邮件、钉钉 Webhook、Slack 等）
5. 保存规则

再补两条常用告警思路：

| 告警 | PromQL 思路 |
| --- | --- |
| 接口 5xx 飙升 | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` |
| 堆内存过高 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` |

完成本步后：**指标可看、可告警，观测闭环完成。**

---

## 端到端验收清单

按顺序打勾：

1. `curl /actuator/prometheus` 有 `jvm_*` 指标
2. Prometheus **Targets** 状态为 UP
3. Prometheus Graph 能查出 `jvm_memory_used_bytes`
4. Grafana 数据源测试通过
5. 导入 JVM Dashboard 有实时曲线
6. 制造一次流量后，HTTP / 业务面板有数据
7. 人为停应用 1 分钟，`up == 0` 告警能触发（若已配通知）

---

## 推荐目录结构（本机演示）

```text
observability-demo/
├── app/                          # Spring Boot 工程
├── prometheus/
│   └── prometheus.yml
├── docker-compose.yml            # Prometheus + Grafana
└── README.md
```

`docker-compose.yml` 最小版：

```yaml
services:
  prometheus:
    image: prom/prometheus:v2.54.1
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:11.2.0
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
```

---

## 生产落地注意点

1. **安全**：`/actuator/prometheus` 仅内网或独立管理端口暴露
2. **标签基数**：禁止把订单号、用户 ID 作为 metric label
3. **抓取周期**：一般 `15s`；高基数服务可适当加大，减轻压力
4. **多环境**：用 `env`、`application`、`instance` 标签区分，避免面板串环境
5. **告警降噪**：先 `for: 1m/5m` 再通知，避免抖动误报
6. **保留时长**：Prometheus 本地默认保留约 15 天；长期存储考虑 Thanos / Mimir 等

---

## 小结

| 步骤 | 你完成的事 | 成功标志 |
| --- | --- | --- |
| 1. Micrometer | 应用内聚合并导出指标 | `/actuator/prometheus` 有数据 |
| 2. Prometheus | 定时拉取并存储 | Targets UP，PromQL 可查 |
| 3. Grafana | 画图与告警 | Dashboard 有曲线，规则可触发 |

记住一句话：**Micrometer 负责产出，Prometheus 负责保管与查询，Grafana 负责给人看和叫醒人。** 三步跑通后，再按业务补自定义指标与告警即可。
