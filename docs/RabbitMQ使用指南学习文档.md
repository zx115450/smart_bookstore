# smart_bookstore RabbitMQ 使用指南

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：使用指南 · 对照本仓库实现  
> **关联包**：`com.zx.bookstore.seckill` · `com.zx.bookstore.trade` · `com.zx.config.mq`  
> **加深阅读**：[消息队列](./learning/消息队列.md) · [消息模型与投递语义](./learning/消息模型与投递语义.md) · [MQ 重试与 Spring Template](./learning/MQ本地重试与Spring-Template学习文档.md)

本文说明 **本项目如何用 RabbitMQ**，不讲通用概念百科。读完应能：本地起 Broker、看懂两条业务拓扑、按现有模式发消息 / 消费 / 重试 / 进死信。下文代码均摘自本仓库，路径已标注。

---

## 1. 项目里 MQ 干什么

| 场景 | 目的 | 入口 |
| --- | --- | --- |
| 秒杀异步发券 | Lua 抢券成功后立刻返回「排队中」，落库发券异步完成 | `SeckillMqProducer` → `seckill.order` |
| 购书超时关单 | 下单后延迟约 15 分钟，仍未支付则取消 | `TradeMqProducer` → `trade.order.timeout` |

共性约定：

- 交换机 / 队列在 Spring `@Configuration` 里声明，启动时自动建拓扑
- 消息体 JSON（`JacksonJsonMessageConverter`）
- 消费端 **手动 ACK**（`acknowledge-mode: manual`）
- 失败默认走 **Broker retry 队列**（TTL + DLX 回主队列），耗尽后进 **DLQ**
- 业务幂等靠状态机 / 唯一键，不依赖「消息只投递一次」

依赖：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

## 2. 本地快速上手

### 2.1 启动 Broker

```bash
docker compose up -d rabbitmq
```

| 项 | 默认值 |
| --- | --- |
| AMQP | `localhost:5672` |
| 管理台 | [http://localhost:15672](http://localhost:15672) |
| 账号 | `guest` / `guest` |

镜像：`rabbitmq:3.13-management-alpine`（见 `docker-compose.yml`）。

### 2.2 启用延迟消息插件（购书超时必需）

购书超时使用 `x-delayed-message` 交换机。官方 management 镜像 **默认不带** 该插件，需自行安装并启用：

```bash
# 容器内示意（插件 jar 需按 RabbitMQ 版本下载后放入 plugins 目录）
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

未启用时，应用启动声明 `trade.delayed` 会失败。秒杀链路只用 Topic + Direct，不依赖该插件。

### 2.3 应用连接配置

敏感项写在 `application-local.yaml`（从 `application-local.yaml.example` 复制）：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

`application.yaml` 中与消费相关的固定项：

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
```

重试与超时延迟：

```yaml
bookstore:
  trade:
    unpaid-cancel-delay-minutes: 15
    # unpaid-cancel-delay-ms: 60000   # 联调可改为 1 分钟
  mq:
    retry:
      enabled: true
      strategy: queue          # 或 local
      max-attempts: 3
      initial-interval-ms: 1000
      multiplier: 2.0
      max-interval-ms: 10000
```

### 2.4 队列参数变更注意

若本地已存在 **参数不一致** 的同名队列（例如改过 DLX / TTL 参数），Spring 无法原地改参，需在管理台删除旧队列后再启动。`RabbitMqConfig` 注释中已提示这一点。

---

## 3. 代码地图

| 职责 | 路径 |
| --- | --- |
| 秒杀拓扑 + `RabbitTemplate` Bean | `seckill/config/RabbitMqConfig.java` |
| 秒杀发送 | `seckill/service/SeckillMqProducer.java` |
| 秒杀主消费 / 死信 | `seckill/consumer/SeckillOrderConsumer.java` · `SeckillDeadLetterConsumer.java` |
| 购书超时拓扑 | `trade/config/TradeMqConfig.java` |
| 超时发送 | `trade/service/TradeMqProducer.java` |
| 超时主消费 / 死信 | `trade/consumer/TradeOrderTimeoutConsumer.java` · `TradeOrderTimeoutDeadLetterConsumer.java` |
| 统一重试 | `config/mq/MqQueueRetrySupport.java` · `MqRetrySupport.java` · `BookstoreMqRetryProperties.java` |

---

## 4. 拓扑总览

### 4.1 秒杀：Topic + Retry + DLQ

```text
Producer
  └─ bookstore.topic ──routingKey=seckill.order──► seckill.order
                                                      │
                         失败且可重试：发到 seckill.order.retry（无消费者）
                                                      │ per-message TTL
                                                      │ 到期 DLX → bookstore.topic / seckill.order
                                                      │
                         超次 / 不可重试：Nack(requeue=false)
                                                      ▼
                                               seckill.dlx ──► seckill.order.dlq
                                                      │
                                                      ▼
                                           SeckillDeadLetterConsumer（对账）
```

源码：`src/main/java/com/zx/bookstore/seckill/config/RabbitMqConfig.java`

```java
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "bookstore.topic";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";
    public static final String SECKILL_ORDER_RETRY_QUEUE = "seckill.order.retry";
    public static final String SECKILL_DLX = "seckill.dlx";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";
    public static final String SECKILL_ORDER_DLQ_ROUTING_KEY = "seckill.order.dlq";

    @Bean
    TopicExchange bookstoreTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange seckillDeadLetterExchange() {
        return new DirectExchange(SECKILL_DLX, true, false);
    }

    @Bean
    Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX)
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    /** 无消费者；per-message TTL 到期后经 DLX 回到 bookstore.topic / seckill.order */
    @Bean
    Queue seckillOrderRetryQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue seckillOrderDeadLetterQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DLQ).build();
    }

    @Bean
    Binding seckillOrderBinding(Queue seckillOrderQueue, TopicExchange bookstoreTopicExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(bookstoreTopicExchange)
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    Binding seckillOrderDeadLetterBinding(Queue seckillOrderDeadLetterQueue,
                                          DirectExchange seckillDeadLetterExchange) {
        return BindingBuilder.bind(seckillOrderDeadLetterQueue)
                .to(seckillDeadLetterExchange)
                .with(SECKILL_ORDER_DLQ_ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  JacksonJsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
```

### 4.2 购书超时：延迟交换机 + Reentry + DLQ

```text
下单事务提交后
  └─ trade.delayed (x-delayed-message)
        header x-delay = 取消延迟毫秒
        routingKey = trade.order.delay
              │
              ▼（到期）
        trade.order.timeout
              │
              │ 失败可重试 → trade.order.timeout.retry（TTL）
              │                 │ 到期 DLX
              │                 └── trade.timeout.reentry ──► trade.order.timeout
              │                     （故意不走 trade.delayed，避免再等 15 分钟）
              │
              │ 超次 / 不可重试 → Nack
              ▼
        trade.timeout.dlx ──► trade.order.timeout.dlq
              │
              ▼
        再试关单；仍失败 → 写 trade_order_timeout_fail 后 Ack
```

源码：`src/main/java/com/zx/bookstore/trade/config/TradeMqConfig.java`

```java
@Configuration
public class TradeMqConfig {

    public static final String TRADE_DELAYED_EXCHANGE = "trade.delayed";
    public static final String TRADE_ORDER_TIMEOUT_QUEUE = "trade.order.timeout";
    public static final String TRADE_ORDER_TIMEOUT_ROUTING_KEY = "trade.order.delay";
    public static final String TRADE_TIMEOUT_REENTRY_EXCHANGE = "trade.timeout.reentry";
    public static final String TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY = "trade.order.timeout";
    public static final String TRADE_ORDER_TIMEOUT_RETRY_QUEUE = "trade.order.timeout.retry";
    public static final String TRADE_TIMEOUT_DLX = "trade.timeout.dlx";
    public static final String TRADE_ORDER_TIMEOUT_DLQ = "trade.order.timeout.dlq";
    public static final String TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY = "trade.order.timeout.dlq";

    @Bean
    CustomExchange tradeDelayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(TRADE_DELAYED_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    DirectExchange tradeTimeoutReentryExchange() {
        return new DirectExchange(TRADE_TIMEOUT_REENTRY_EXCHANGE, true, false);
    }

    @Bean
    Queue tradeOrderTimeoutQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", TRADE_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue tradeOrderTimeoutRetryQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", TRADE_TIMEOUT_REENTRY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue tradeOrderTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_DLQ).build();
    }

    @Bean
    DirectExchange tradeTimeoutDeadLetterExchange() {
        return new DirectExchange(TRADE_TIMEOUT_DLX, true, false);
    }

    @Bean
    Binding tradeOrderTimeoutBinding(Queue tradeOrderTimeoutQueue, CustomExchange tradeDelayedExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutQueue)
                .to(tradeDelayedExchange)
                .with(TRADE_ORDER_TIMEOUT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    Binding tradeOrderTimeoutReentryBinding(Queue tradeOrderTimeoutQueue,
                                            DirectExchange tradeTimeoutReentryExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutQueue)
                .to(tradeTimeoutReentryExchange)
                .with(TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY);
    }

    @Bean
    Binding tradeOrderTimeoutDeadLetterBinding(Queue tradeOrderTimeoutDeadLetterQueue,
                                               DirectExchange tradeTimeoutDeadLetterExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutDeadLetterQueue)
                .to(tradeTimeoutDeadLetterExchange)
                .with(TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY);
    }
}
```

---

## 5. 场景一：秒杀异步发券

### 5.1 链路

1. `SeckillService.grab`：令牌桶限流 → Redis Lua 扣库存 / 记用户
2. 构造 `SeckillOrderMessage(userId, activityId, idempotencyKey)`
3. `SeckillMqProducer.publishSeckillOrder` 发到 `bookstore.topic` / `seckill.order`
4. **发送失败**：`rollbackGrab` 回滚 Redis，接口报系统繁忙
5. 接口返回 `PROCESSING`；前端轮询 `getResult`
6. `SeckillOrderConsumer` 调 `processSeckillOrder`：幂等查重 → 写订单 → 发券
7. 发券失败：订单标 `FAILED`、回滚 Redis，**正常返回让主队列 Ack**（避免事务回滚冲掉 FAILED）
8. 早期参数 / 配置类异常：抛出 → 重试或进 DLQ → `reconcileDeadLetter` 对账

### 5.2 消息体

`src/main/java/com/zx/bookstore/seckill/dto/SeckillOrderMessage.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {

    private Long userId;
    private Long activityId;
    private String idempotencyKey;
}
```

### 5.3 抢券成功后发送（含失败回滚）

`SeckillService.grab` 片段：

```java
SeckillOrderMessage message = new SeckillOrderMessage(
        principal.userId(), activityId, req.getIdempotencyKey());
try {
    seckillMqProducer.publishSeckillOrder(message);
} catch (Exception e) {
    log.error("publish seckill order failed, userId={}, activityId={}",
            principal.userId(), activityId, e);
    seckillRedisService.rollbackGrab(activityId, principal.userId());
    throw SeckillException.systemBusy();
}

SeckillGrabResponse response = new SeckillGrabResponse();
response.setActivityId(activityId);
response.setStatus(SeckillOrderStatus.PROCESSING.name());
response.setMessage("排队中，请稍后查询结果");
return response;
```

生产者：`src/main/java/com/zx/bookstore/seckill/service/SeckillMqProducer.java`

```java
@Service
@RequiredArgsConstructor
public class SeckillMqProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishSeckillOrder(SeckillOrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                message
        );
    }
}
```

### 5.4 主消费者（手动 ACK + 重试）

`src/main/java/com/zx/bookstore/seckill/consumer/SeckillOrderConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillService seckillService;
    private final MqRetrySupport mqRetrySupport;
    private final MqQueueRetrySupport mqQueueRetrySupport;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_QUEUE)
    public void consume(SeckillOrderMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = MqQueueRetrySupport.RETRY_COUNT_HEADER, required = false)
                        Integer retryCount) throws IOException {
        try {
            mqRetrySupport.execute("seckill.order",
                    () -> seckillService.processSeckillOrder(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            if (mqQueueRetrySupport.tryScheduleRetry(
                    "seckill.order",
                    message,
                    retryCount,
                    RabbitMqConfig.SECKILL_ORDER_RETRY_QUEUE,
                    e)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.error("consume seckill order failed → dead letter, message={}, retryCount={}",
                    message, retryCount, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

ACK 语义：

```text
成功                          → basicAck
可重试失败（QUEUE 策略）      → tryScheduleRetry 成功则 Ack（原消息离开主队列）
超次 / BusinessException 等   → basicNack(..., requeue=false) → DLQ
```

### 5.5 死信对账

`src/main/java/com/zx/bookstore/seckill/consumer/SeckillDeadLetterConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillDeadLetterConsumer {

    private final SeckillService seckillService;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_DLQ)
    public void consume(SeckillOrderMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            seckillService.reconcileDeadLetter(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("reconcile seckill dead letter failed, message={}", message, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

对账失败也 **不 requeue**，依赖日志告警人工介入。

---

## 6. 场景二：购书超时关单

### 6.1 链路

1. `TradeService` 创建订单落库后，`scheduleOrderTimeoutCancel`
2. **事务提交后再发**（`TransactionSynchronization.afterCommit`），避免脏消息
3. `TradeMqProducer` 往 `trade.delayed` 发消息，并设置 `x-delay`
4. 到期进入 `trade.order.timeout`，`cancelOnTimeout`：仅 `PENDING_PAY` 才取消
5. 已支付 / 已取消：直接跳过（幂等）
6. 主消费失败 → retry / DLQ；DLQ 再失败 → `TradeOrderTimeoutFailService.recordFailure` 落库后 Ack

当前设计：支付时才扣库存与核销券，超时关单 **无需回滚库存 / 券**。

### 6.2 消息体

`src/main/java/com/zx/bookstore/trade/dto/TradeOrderTimeoutMessage.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderTimeoutMessage {

    private Long orderId;
    private String orderNo;
}
```

### 6.3 事务提交后再发

`TradeService.scheduleOrderTimeoutCancel`：

```java
private void scheduleOrderTimeoutCancel(TradeOrder order) {
    TradeOrderTimeoutMessage message =
            new TradeOrderTimeoutMessage(order.getId(), order.getOrderNo());
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tradeMqProducer.publishOrderTimeout(message);
            }
        });
    } else {
        tradeMqProducer.publishOrderTimeout(message);
    }
}
```

生产者：`src/main/java/com/zx/bookstore/trade/service/TradeMqProducer.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeMqProducer {

    private final RabbitTemplate rabbitTemplate;
    private final BookstoreTradeProperties tradeProperties;

    public void publishOrderTimeout(TradeOrderTimeoutMessage message) {
        long delayMs = tradeProperties.resolveCancelDelayMs();
        rabbitTemplate.convertAndSend(
                TradeMqConfig.TRADE_DELAYED_EXCHANGE,
                TradeMqConfig.TRADE_ORDER_TIMEOUT_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setHeader("x-delay", delayMs);
                    return msg;
                }
        );
        log.info("published trade order timeout message, orderId={}, orderNo={}, delayMs={}",
                message.getOrderId(), message.getOrderNo(), delayMs);
    }
}
```

联调可把 `bookstore.trade.unpaid-cancel-delay-ms` 设为较小值（如 `60000`），不必等 15 分钟。

### 6.4 超时关单业务（幂等）

```java
@Transactional
public void cancelOnTimeout(TradeOrderTimeoutMessage message) {
    if (message == null || message.getOrderId() == null) {
        throw new IllegalArgumentException("超时关单消息缺少 orderId");
    }
    Long orderId = message.getOrderId();
    TradeOrder order = tradeOrderRepository.findById(orderId).orElse(null);
    if (order == null) {
        log.warn("timeout cancel skipped: order not found, orderId={}", orderId);
        return;
    }
    if (!TradeOrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
        log.info("timeout cancel skipped: orderId={}, status={}", orderId, order.getStatus());
        return;
    }
    if (!tradeOrderRepository.markCancelledByTimeout(orderId)) {
        log.info("timeout cancel race lost: orderId={}", orderId);
        return;
    }
    log.info("timeout cancel success: orderId={}, orderNo={}", orderId, order.getOrderNo());
}
```

### 6.5 主消费者

`src/main/java/com/zx/bookstore/trade/consumer/TradeOrderTimeoutConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutConsumer {

    private final TradeService tradeService;
    private final MqRetrySupport mqRetrySupport;
    private final MqQueueRetrySupport mqQueueRetrySupport;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_QUEUE)
    public void consume(TradeOrderTimeoutMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = MqQueueRetrySupport.RETRY_COUNT_HEADER, required = false)
                        Integer retryCount) throws IOException {
        try {
            mqRetrySupport.execute("trade.order.timeout",
                    () -> tradeService.cancelOnTimeout(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            if (mqQueueRetrySupport.tryScheduleRetry(
                    "trade.order.timeout",
                    message,
                    retryCount,
                    TradeMqConfig.TRADE_ORDER_TIMEOUT_RETRY_QUEUE,
                    e)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.error("consume trade order timeout failed → dead letter, message={}, retryCount={}",
                    message, retryCount, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

### 6.6 死信：再试关单 + 失败落库

`src/main/java/com/zx/bookstore/trade/consumer/TradeOrderTimeoutDeadLetterConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutDeadLetterConsumer {

    private final TradeService tradeService;
    private final TradeOrderTimeoutFailService timeoutFailService;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_DLQ)
    public void consume(TradeOrderTimeoutMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            tradeService.cancelOnTimeout(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("reconcile trade order timeout dead letter failed, message={} → persist fail log",
                    message, e);
            try {
                timeoutFailService.recordFailure(message, e);
                channel.basicAck(deliveryTag, false);
            } catch (Exception persistEx) {
                log.error("persist trade timeout fail log also failed, message={} → discard",
                        message, persistEx);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
```

### 6.7 为何要有 `trade.timeout.reentry`

Retry 队列到期后若仍回到 `trade.delayed`，会再次带上业务 `x-delay`，等于再等一整段超时时间。因此 retry 的 DLX 指向 **`trade.timeout.reentry`**，立即回到主队列再消费。

---

## 7. 统一重试框架

配置前缀：`bookstore.mq.retry`。

| 策略 | 行为 | 适用 |
| --- | --- | --- |
| `queue`（默认） | 失败发到 `*.retry`，设 `expiration` + header `x-bookstore-retry-count`，原消息 Ack | 生产推荐，等待在 Broker |
| `local` | 消费线程内 `RetryTemplate` 同步退避；`MqQueueRetrySupport` 不接管 | 简单调试 |

`max-attempts` **含首次消费**。例如 `3`：第 1、2 次失败可进 retry；第 3 次失败 Nack → DLQ。

延迟（QUEUE，第 `n` 次进入 retry，`n` 从 1 起）：

$$
delay_n = \min(initial \times multiplier^{n-1},\ maxDelay)
$$

默认约：1s → 2s → 再失败进 DLQ。

**不重试**（直接视为应进 DLQ）：异常链上存在 `BusinessException` 或 `IllegalArgumentException`。

### 7.1 Broker 侧重试核心

`src/main/java/com/zx/config/mq/MqQueueRetrySupport.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class MqQueueRetrySupport {

    public static final String RETRY_COUNT_HEADER = "x-bookstore-retry-count";

    private final BookstoreMqRetryProperties properties;
    private final RabbitTemplate rabbitTemplate;

    public boolean tryScheduleRetry(String action,
                                    Object payload,
                                    Integer currentRetryCount,
                                    String retryQueueName,
                                    Throwable error) {
        if (!properties.isEnabled() || properties.getStrategy() != MqRetryStrategy.QUEUE) {
            return false;
        }
        if (isNonRetryable(error)) {
            log.warn("mq queue retry skipped (non-retryable), action={}, cause={}",
                    action, error.toString());
            return false;
        }

        int count = currentRetryCount == null ? 0 : Math.max(0, currentRetryCount);
        if (count + 1 >= Math.max(1, properties.getMaxAttempts())) {
            log.warn("mq queue retry exhausted, action={}, retryCount={}, maxAttempts={}",
                    action, count, properties.getMaxAttempts());
            return false;
        }

        int nextCount = count + 1;
        long delayMs = properties.resolveDelayMs(nextCount);
        // 默认交换机 "" + 队列名：直接投递到 retry 等待队列
        rabbitTemplate.convertAndSend("", retryQueueName, payload, message -> {
            message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextCount);
            message.getMessageProperties().setExpiration(String.valueOf(delayMs));
            return message;
        });
        log.warn("mq queue retry scheduled, action={}, retryCount={}/{}, delayMs={}, queue={}",
                action, nextCount, properties.getMaxAttempts(), delayMs, retryQueueName);
        return true;
    }

    static boolean isNonRetryable(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof BusinessException || cursor instanceof IllegalArgumentException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
```

### 7.2 本地策略入口（仅 `strategy: local`）

`src/main/java/com/zx/config/mq/MqRetrySupport.java`

```java
public void execute(String action, Runnable task) {
    boolean useLocal = properties.isEnabled()
            && properties.getStrategy() == MqRetryStrategy.LOCAL
            && properties.getMaxAttempts() > 1;
    if (!useLocal) {
        task.run();  // QUEUE 策略：只执行一次，延迟交给 MqQueueRetrySupport
        return;
    }
    // LOCAL：RetryTemplate 同步退避重试
    AtomicInteger attempts = new AtomicInteger();
    mqRetryTemplate.invoke(() -> {
        int attempt = attempts.incrementAndGet();
        if (attempt > 1) {
            log.warn("mq local retry, action={}, attempt={}/{}",
                    action, attempt, properties.getMaxAttempts());
        }
        task.run();
    });
}
```

更深说明见 [MQ 本地重试与 Spring Template 学习文档](./learning/MQ本地重试与Spring-Template学习文档.md)。

---

## 8. 工程约定（照此扩展）

新增一条 MQ 链路时，建议按本仓库现有模式：

1. **Config**：声明 Exchange / Queue / Binding；主队列挂业务 DLX；另建无消费者的 `*.retry` 队列，DLX 回主队列（若主入口是延迟交换机，retry 回 **reentry Direct**，不要回延迟交换机）
2. **DTO**：字段够幂等与排查即可，避免过大 payload
3. **Producer**：只认 Exchange + Routing Key；需要延迟时用消息头 `x-delay`；与 DB 同事务时用 **afterCommit** 再发
4. **Consumer**：`@RabbitListener` + `Channel` + `DELIVERY_TAG`；成功 Ack；失败走 `MqQueueRetrySupport`；超次 Nack 不 requeue
5. **DLQ Consumer**：补偿 / 对账 / 失败表；避免无限 requeue
6. **幂等**：唯一索引、状态判断、或 idempotencyKey；默认按至少一次投递设计

全局已提供的基础设施：

- `RabbitTemplate` + JSON 转换器：定义在 `RabbitMqConfig`
- 手动 ACK、`prefetch: 10`：`application.yaml`
- 重试属性与 `RetryTemplate`：`com.zx.config.mq`

---

## 9. 运维与排障

| 现象 | 排查 |
| --- | --- |
| 声明 `trade.delayed` 失败 | 未启用 `rabbitmq_delayed_message_exchange` |
| 启动报队列参数不匹配 | 管理台删掉旧队列 / 交换机后重启 |
| 秒杀一直 `PROCESSING` | 看 `seckill.order` 积压、Consumer 日志、发送失败是否已回滚 Redis |
| 订单到期未取消 | 看 `trade.order.timeout` 深度、延迟插件、`x-delay` 是否生效、订单是否已非 `PENDING_PAY` |
| 消息进了 DLQ | 秒杀：对账日志；购书：`trade_order_timeout_fail` 与管理端超时失败查询 |
| 失败风暴占满线程 | 确认 `strategy: queue`，不要用 `local` 扛高峰 |

管理台建议关注：

- Queues：Ready / Unacked 深度
- `*.retry`：应只有短暂积压（TTL 等待）
- `*.dlq`：非空即需对账或告警

---

## 10. 与概念文档的分工

| 文档 | 用途 |
| --- | --- |
| **本文** | 本仓库怎么用、拓扑、配置、扩展清单 |
| [消息模型与投递语义](./learning/消息模型与投递语义.md) | Exchange / ACK / 至少一次等理论 |
| [消息队列](./learning/消息队列.md) | 学习路线与能力自检 |
| [MQ 重试文档](./learning/MQ本地重试与Spring-Template学习文档.md) | QUEUE vs LOCAL、`RetryTemplate` |
| [P4 秒杀业务流程与技术栈](./P4秒杀业务流程与技术栈.md) | 秒杀业务全链路 |

---

## 11. 小结

- 两条业务线：**秒杀削峰异步落库**、**购书延迟关单**
- 失败路径统一：**有限次 Broker retry → DLQ → 对账 / 失败表**
- 可靠业务靠：**事务后发消息、手动 ACK、幂等、死信补偿**，而不是「MQ 保证恰好一次」
