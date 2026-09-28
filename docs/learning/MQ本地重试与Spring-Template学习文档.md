# MQ 重试：Retry 队列与 Spring Template

> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md) · [消息队列](./消息队列.md)

默认策略为 **Broker retry 队列（TTL + DLX 回主队列）**，可选本地 `RetryTemplate`。本文说明：

1. QUEUE / LOCAL 两种策略与调用链
2. `RetryTemplate` 与 Spring `*Template` 写法
3. 为何生产更推荐把等待放在 MQ 而不是消费线程 sleep

---

## 1. 默认：Retry 队列（`strategy: queue`）

```text
主队列消费失败（未超次、可重试）
  → 发到 *.retry（header x-bookstore-retry-count + per-message TTL）
  → 原消息 Ack（线程释放）
  → TTL 到期 → DLX 回到主队列再消费
  → 超次或 BusinessException / IllegalArgumentException
  → Nack(requeue=false) → 业务 DLQ → 对账 / 失败表
```

| 队列 | 作用 |
| --- | --- |
| `seckill.order` | 秒杀主队列 |
| `seckill.order.retry` | 等待队列（无消费者），DLX → `bookstore.topic` / `seckill.order` |
| `seckill.order.dlq` | 最终死信对账 |
| `trade.order.timeout` | 购书超时主队列 |
| `trade.order.timeout.retry` | 等待队列，DLX → `trade.timeout.reentry` → 主队列 |
| `trade.order.timeout.dlq` | 最终死信 |

购书侧用 `trade.timeout.reentry`，避免 retry 回来再走业务 `x-delay` 的 15 分钟延迟。

### 1.1 关键类

| 类 | 职责 |
| --- | --- |
| `MqQueueRetrySupport` | 判断是否可重试、算延迟、发到 retry 队列 |
| `BookstoreMqRetryProperties` | `strategy` / `max-attempts` / 退避参数 |
| `SeckillOrderConsumer` / `TradeOrderTimeoutConsumer` | 成功 Ack；可重试则 schedule+Ack；否则 Nack |

### 1.2 次数语义

header `x-bookstore-retry-count`：已进入 retry 队列的次数，首次投递为 0。

| 场景（`max-attempts: 3`） | retryCount | 失败后 |
| --- | --- | --- |
| 第 1 次消费 | 0 | 发 retry（count=1）并 Ack |
| 第 2 次消费 | 1 | 发 retry（count=2）并 Ack |
| 第 3 次消费 | 2 | Nack → DLQ |

延迟：

$$
delay_n = \min(initial \times multiplier^{n-1},\ maxDelay)
$$

其中 `n` 为即将写入的 `retryCount`（从 1 起）。

### 1.3 配置

```yaml
bookstore:
  mq:
    retry:
      enabled: true
      strategy: queue          # 或 local
      max-attempts: 3
      initial-interval-ms: 1000
      multiplier: 2.0
      max-interval-ms: 10000
```

### 1.4 相对本地 sleep 的优点

- 等待在 Broker，**不占用消费线程**
- 失败风暴时不易把 listener 线程打满
- 仍用有限次数 + 最终 DLQ，避免毒消息死循环

---

## 2. 可选：本地重试（`strategy: local`）

```text
Consumer
  └─ MqRetrySupport.execute
        └─ RetryTemplate.invoke（Spring Framework 7）
              └─ 业务；失败则线程内 sleep 再试
  └─ 仍失败 → Nack → DLQ
```

| 类 | 归属 |
| --- | --- |
| `MqRetrySupport` | 本项目门面；仅 `LOCAL` 时启用多次尝试 |
| `RetryTemplate` | Spring Framework 7 |
| `MqRetryConfiguration` | 组装 `RetryPolicy` |

`QUEUE` 策略下 `MqRetrySupport` 只执行一次，延迟交给 `MqQueueRetrySupport`。

Spring 7：`maxRetries = maxAttempts - 1`，总次数 = `1 + maxRetries`。

官方：[Resilience Features](https://docs.spring.io/spring-framework/reference/core/resilience.html)

---

## 3. Spring `*Template` 一般怎么写

共同模式（模板方法）：

1. 固定流程写在 Template（开资源、重试、序列化、异常翻译）
2. 变化点交给回调（SQL / HTTP / 发消息 / 业务）

| Template | 变化点 |
| --- | --- |
| `JdbcTemplate` | SQL + `RowMapper` |
| `RestTemplate` | URL / body |
| `RabbitTemplate` | Exchange + routingKey + payload |
| `RetryTemplate` | 要重试的 `Runnable` |

本仓库：`SeckillMqProducer` / `TradeMqProducer` 用 `RabbitTemplate`；消费重试用 `RetryTemplate`（LOCAL）或队列 TTL（QUEUE）。

---

## 4. 主路径时序（QUEUE）

```text
投递主队列
  → 业务失败
  → tryScheduleRetry == true  → Ack，消息在 retry 队列睡 delay
  → TTL 到期 DLX 回主队列（带 x-bookstore-retry-count）
  → 再失败直至耗尽
  → Nack → DLQ → SeckillDeadLetterConsumer / TradeOrderTimeoutDeadLetterConsumer
```

注意：若本地已有**无 retry / 参数不一致**的旧队列，需在管理台删除后重启，否则声明失败。

---

## 5. 对照阅读

- `com.zx.config.mq.*`
- `RabbitMqConfig` / `TradeMqConfig`
- `SeckillOrderConsumer` / `TradeOrderTimeoutConsumer`
- 单测：`MqQueueRetrySupportTest`、`MqRetrySupportTest`
- [消息队列](./消息队列.md)、[消息模型与投递语义](./消息模型与投递语义.md)

---

## 6. 自测问题

- [ ] QUEUE 与 LOCAL 等待分别发生在哪里？
- [ ] `max-attempts: 3` 时 `retryCount=2` 再失败会怎样？
- [ ] 为何购书 retry 要经 `trade.timeout.reentry` 而不是 `trade.delayed`？
- [ ] 为何 `BusinessException` 不进 retry 队列？
- [ ] `JdbcTemplate` / `RabbitTemplate` / `RetryTemplate` 的共同点是什么？
