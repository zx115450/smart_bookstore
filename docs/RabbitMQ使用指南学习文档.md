# 从秒杀 / 订单超时学 RabbitMQ

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 关联本仓库业务代码  
> **关联包 / 模块**：`com.zx.bookstore.seckill / trade`  
> **建议前置**：[消息队列](./learning/消息队列.md)、[P4 秒杀业务流程与技术栈](./P4秒杀业务流程与技术栈.md)

## 学习目标

1. 理解 Exchange、Queue、Routing Key 与死信拓扑
2. 掌握生产者发消息与消费者手动 ACK 模式
3. 分析秒杀异步落库与订单超时两类 MQ 场景

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/zx/bookstore/seckill/config/RabbitMqConfig.java` | 对照阅读 |
| `src/main/java/com/zx/bookstore/seckill/consumer/SeckillOrderConsumer.java` | 对照阅读 |
| `src/main/java/com/zx/bookstore/trade/config/TradeMqConfig.java` | 对照阅读 |
| `src/main/java/com/zx/bookstore/trade/consumer/TradeOrderTimeoutConsumer.java` | 对照阅读 |

读理论时请打开上表文件对照；改代码前先跑通 `docker compose up -d` 与 `local` profile。

---

## 导读
很多人第一次用 RabbitMQ，会卡在这几个问题上：

1. **消息到底发给谁？** 是发给队列，还是发给交换机？
2. **Exchange、Queue、Binding、Routing Key** 分别干什么？
3. 消费失败了怎么办？ACK、NACK、重试、死信又是什么关系？
4. 什么时候用 Direct，什么时候用 Topic / Fanout？

本文按「先关系、再机制、后样例」的顺序讲清楚。读完你应该能自己画出一张拓扑图，并知道失败消息会去哪。

---

## 一、先建立心智模型

RabbitMQ 是一个 **消息中间件（Broker）**。生产者不直接把消息塞给某个消费者，而是：

```text
Producer（生产者）
    │  发布消息
    ▼
Exchange（交换机）──按规则路由──► Queue（队列）──投递──► Consumer（消费者）
         ▲
         │ Binding（绑定）+ Routing Key（路由键）
```

记住三句话：

1. **生产者只认识 Exchange**（多数情况下不直接往队列里塞）
2. **消费者只认识 Queue**（从队列里取消息）
3. **Binding 决定「交换机里的消息如何进入哪个队列」**

可以把 Exchange 想成邮局分拣中心，Queue 想成各个信箱，Binding 是分拣规则。

---

## 二、核心组件关系

### 2.1 组件一览

| 组件 | 英文 | 作用 |
|------|------|------|
| 生产者 | Producer | 发送消息的应用 |
| 交换机 | Exchange | 接收消息，按规则转发到队列 |
| 队列 | Queue | 消息的缓冲区，消费者从这里取 |
| 绑定 | Binding | Exchange 与 Queue 之间的路由规则 |
| 路由键 | Routing Key | 发布/绑定时用的「标签」，参与匹配 |
| 消费者 | Consumer | 从队列消费并处理业务 |
| 虚拟主机 | Virtual Host | 逻辑隔离（类似命名空间），权限按 vhost 划分 |
| 连接/信道 | Connection / Channel | TCP 连接上可开多个 Channel，操作都在 Channel 上完成 |

### 2.2 关系图（必看）

```text
                    ┌─────────────────────────────────────┐
                    │              Broker                 │
                    │                                     │
  Producer ───────► │  Exchange                           │
                    │     │                               │
                    │     │ Binding (routing key 规则)     │
                    │     ├──────────► Queue A ──► Consumer 1
                    │     ├──────────► Queue B ──► Consumer 2
                    │     └──────────► Queue C ──► Consumer 3
                    │                                     │
                    └─────────────────────────────────────┘
```

- **一条消息进入一个 Exchange**
- **可能进入 0 个、1 个或多个 Queue**（取决于交换机类型和绑定）
- **一个 Queue 可被多个 Consumer 竞争消费**（默认轮询分发）

### 2.3 为什么不让生产者直接写队列？

解耦。业务方只约定「发到哪个交换机、带什么 routing key」，不必知道下游有几个队列、几个服务在消费。以后加一个审计队列，只需多绑一条 Binding，生产者代码不用改。

---

## 三、交换机类型（怎么路由）

RabbitMQ 常见四种 Exchange。**类型决定「routing key 怎么用」**。

### 3.1 Direct（直连）

**规则**：队列绑定的 routing key **完全相等** 才投递。

```text
Exchange(direct)  "order"
  ├─ binding key = "order.pay"     → queue.order.pay
  └─ binding key = "order.cancel"  → queue.order.cancel

发布 routingKey="order.pay" → 只进 queue.order.pay
```

适合：明确的一对一/少对少路由，如「支付成功通知」「取消通知」分开处理。

### 3.2 Topic（主题）

**规则**：routing key 按 `.` 分段，支持通配符：

| 通配符 | 含义 |
|--------|------|
| `*` | 匹配 **恰好一段** |
| `#` | 匹配 **零段或多段** |

```text
Exchange(topic)  "biz.topic"
  ├─ "order.*"     → 匹配 order.pay、order.cancel（一段）
  ├─ "order.#"     → 匹配 order、order.pay、order.pay.success（多段）
  └─ "*.error"     → 匹配 pay.error、sms.error

发布 "order.pay.success"
  → 进绑定了 order.# 的队列
  → 不进 order.*（因为多了一段）
```

适合：日志分级、业务事件总线、一个交换机挂多种下游。

### 3.3 Fanout（广播）

**规则**：**忽略** routing key，绑定到该交换机的 **所有队列** 都收到一份拷贝。

```text
Exchange(fanout)  "notify.fanout"
  ├─ → queue.sms
  ├─ → queue.email
  └─ → queue.push

发布任意消息 → 三个队列各一份
```

适合：同一事件通知多个独立系统（短信、邮件、站内信）。

### 3.4 Headers（较少用）

按消息头（headers）匹配，而不是 routing key。灵活但不如 Topic 直观，生产中相对少见。

### 3.5 选型速查

| 场景 | 推荐 |
|------|------|
| 固定几种业务类型，精确投递 | Direct |
| 事件多、要按模式订阅 | Topic |
| 一发多收、完全广播 | Fanout |
| 需要按自定义属性过滤 | Headers |

---

## 四、队列与消息属性

### 4.1 队列常见参数

| 参数/特性 | 含义 |
|-----------|------|
| durable | 队列元数据持久化，Broker 重启后队列还在 |
| exclusive | 仅当前连接可见，连接断开队列删除 |
| autoDelete | 最后一个消费者断开后自动删除 |
| TTL（消息/队列） | 超时未消费则过期（可进死信） |
| max-length | 队列最大长度，超限可丢弃或进死信 |
| 惰性队列 lazy | 尽量落盘，适合堆积场景 |

### 4.2 消息持久化

- 队列 `durable=true` **不等于** 消息一定不丢  
- 消息还需 `deliveryMode=2`（持久化）  
- 生产端建议开启 **Publisher Confirm**，确认 Broker 已接收  

三者配合，才能在宕机场景下尽量不丢（仍非 100%，极端情况要靠业务幂等）。

### 4.3 消息结构（概念）

```text
Envelope
  ├─ Exchange / Routing Key
  ├─ Properties（contentType、messageId、headers、expiration…）
  └─ Body（业务 JSON / 二进制）
```

业务上通常把 JSON 放在 Body，把重试次数、追踪 ID 放在 Headers。

---

## 五、生产与消费的基本流程

### 5.1 生产端伪代码（概念）

```java
// 1. 建立连接与信道
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();

// 2. 声明交换机、队列、绑定（幂等，可重复声明）
channel.exchangeDeclare("biz.topic", "topic", true);
channel.queueDeclare("queue.order.pay", true, false, false, null);
channel.queueBind("queue.order.pay", "biz.topic", "order.pay");

// 3. 发布
String body = "{\"orderId\":1001,\"amount\":99.00}";
channel.basicPublish(
    "biz.topic",          // exchange
    "order.pay",          // routing key
    MessageProperties.PERSISTENT_TEXT_PLAIN,
    body.getBytes()
);
```

### 5.2 消费端伪代码（概念）

```java
channel.basicQos(10); // 预取：未 ACK 前最多塞给该消费者 10 条

channel.basicConsume("queue.order.pay", false, (consumerTag, delivery) -> {
    try {
        String json = new String(delivery.getBody());
        handle(json); // 业务处理
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } catch (Exception e) {
        // 见下文 ACK / NACK
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
    }
}, consumerTag -> { });
```

注意：`autoAck=false` 时必须自己 Ack/Nack，否则消息会一直处于 Unacked，最终可能被重新投递。

---

## 六、各种处理机制（重点）

### 6.1 确认机制：ACK / NACK / Reject

| 操作 | 含义 |
|------|------|
| `basicAck` | 处理成功，Broker 删除该消息 |
| `basicNack` | 处理失败；可指定是否 `requeue` |
| `basicReject` | 类似 Nack，但不支持批量 |

```text
成功 ──────────────────────────────► Ack ──► 消息从队列删除

失败 + requeue=true ───────────────► 消息重新回到队列（可能立刻再被消费）
失败 + requeue=false ──────────────► 丢弃；若配置了死信，则进入死信交换机
```

**坑**：对「毒消息」（怎么处理都失败）使用 `requeue=true`，会形成死循环打满 CPU。生产上通常：`requeue=false` + 死信，或有限次重试后再进死信。

### 6.2 预取 Prefetch（QoS）

```text
basicQos(prefetchCount = N)
```

含义：消费者最多有 N 条 **未确认** 消息。  
作用：避免一个慢消费者被塞爆，也避免公平性太差。

经验：

- CPU 型任务：prefetch 可以小一点（如 1～10）  
- 快速 IO 型：可适当增大  

### 6.3 竞争消费与公平分发

同一队列挂多个 Consumer：

```text
Queue ──► Consumer A
      └─► Consumer B
```

默认按轮询分发。谁 Ack 快，谁更能继续拿新消息（配合 prefetch）。

这是水平扩展消费者的基础：**加机器 = 加 Consumer**，不用改生产者。

### 6.4 消息 TTL 与队列 TTL

- **消息 TTL**：单条消息的过期时间  
- **队列 TTL**：队列里所有消息的统一过期时间  

过期后消息被丢弃；若队列配置了死信交换机，则进入死信链路。  
常用于：订单超时关闭的「延迟效果」（配合死信，见样例 3）。

### 6.5 死信（DLX / DLQ）

**死信交换机（Dead Letter Exchange）** 不是特殊类型，只是一个普通 Exchange，被指定为「死信去处」。

消息变成死信的常见原因：

1. 消费者 `Nack/Reject` 且 `requeue=false`  
2. 消息 TTL 过期  
3. 队列达到最大长度，消息被挤出  

```text
业务队列 order.queue
  参数：
    x-dead-letter-exchange = order.dlx
    x-dead-letter-routing-key = order.dead

失败 / 过期 / 超长
        │
        ▼
   order.dlx ──► order.dead.queue（死信队列）
                      │
                      ▼
                 对账 / 告警 / 人工处理
```

死信的价值：**失败消息与正常流量隔离**，主队列继续跑，失败可事后补偿。

### 6.6 幂等消费

MQ **至少一次投递** 很常见（网络重试、Consumer 重启未 Ack 等），所以业务必须幂等：

```text
同一 messageId / 业务唯一键 处理两次 = 只产生一次副作用
```

常见手段：

- 数据库唯一索引（订单号、幂等键）  
- Redis `SETNX` 处理标记  
- 状态机：只允许 `CREATED → PAID`，重复支付直接返回成功  

**没有幂等的重试/死信，都可能把账做花。**

### 6.7 发布确认与返回（生产端可靠）

| 机制 | 作用 |
|------|------|
| Publisher Confirm | Broker 确认已收到（或写入磁盘策略满足） |
| Mandatory + Return | 消息无法路由到任何队列时退回生产者 |

适合对「发出去」有强要求的场景；和消费端 Ack 是两回事。

### 6.8 事务 vs Confirm

Channel 事务能保证发布原子性，但性能差。现代实践更推荐 **Publisher Confirm**，而不是 AMQP 事务。

---

## 七、样例

### 样例 1：Direct —— 订单支付结果通知

**需求**：支付成功、支付失败走不同处理逻辑。

```text
Exchange: order.direct (direct)
  order.pay.success → queue.pay.success
  order.pay.fail    → queue.pay.fail
```

发布：

```java
channel.basicPublish("order.direct", "order.pay.success", null, body);
```

消费成功队列的服务发积分；失败队列的服务发站内信。互不影响。

---

### 样例 2：Topic —— 日志收集

**需求**：所有 `*.error` 进告警队列；`order.#` 进订单分析队列。

```text
Exchange: log.topic (topic)
  *.error   → queue.alert
  order.#   → queue.order.analytics
```

```text
发布 routingKey = "order.pay.error"
  → 同时进入 queue.alert 和 queue.order.analytics（各一份拷贝）
```

这就是 Topic 的典型用法：**一条事件，多种订阅。**

---

### 样例 3：TTL + 死信 ——「延迟 30 分钟」关单（简化版）

RabbitMQ 本身没有「延迟队列」一等公民（可用插件），经典做法是：

```text
1. 消息发到 delay.queue，设置 TTL = 30min
2. delay.queue 不挂业务消费者
3. 过期后经 DLX 进入 close.order.queue
4. close.order.queue 的消费者执行「若仍未支付则关单」
```

```text
Producer ──► delay.exchange ──► delay.queue (TTL=30m, DLX=close.exchange)
                                      │ 到期
                                      ▼
                               close.exchange ──► close.order.queue ──► Consumer
```

注意：队列级 TTL 时，过期顺序与队列头有关；高精度延迟更推荐延迟插件或时间轮方案。

---

### 样例 4：Fanout —— 用户注册成功后的多通道通知

```text
Exchange: user.registered (fanout)
  → queue.mail
  → queue.sms
  → queue.growth（成长体系发新人礼）
```

注册服务只发一条「用户已注册」事件，三个下游独立扩缩容。某个通知挂了，不影响另外两个（各自队列堆积）。

---

### 样例 5：消费失败进死信 + 对账（推荐心智）

```text
主队列 work.queue
  成功 → Ack
  失败 → Nack(requeue=false) → DLQ

死信队列 work.dlq
  → 记录日志 / 告警
  → 根据业务键做补偿（回滚库存、标记失败单等）
  → 一般不要无限重投主队列
```

伪代码：

```java
void onMessage(Message msg, Channel channel, long tag) throws IOException {
    try {
        process(msg);           // 业务，内部保证幂等
        channel.basicAck(tag, false);
    } catch (Exception e) {
        log.error("process failed", e);
        channel.basicNack(tag, false, false); // 进死信，不 requeue
    }
}

void onDeadLetter(Message msg, Channel channel, long tag) throws IOException {
    try {
        reconcile(msg);         // 对账补偿，而不是盲目重试
        channel.basicAck(tag, false);
    } catch (Exception e) {
        log.error("reconcile failed, need manual", e);
        channel.basicNack(tag, false, false); // 避免死信里死循环
    }
}
```

---

## 八、Spring AMQP 声明拓扑（示意）

概念与原生客户端一致，只是用 Bean 声明：

```java
@Bean
DirectExchange orderExchange() {
    return new DirectExchange("order.direct", true, false);
}

@Bean
Queue paySuccessQueue() {
    return QueueBuilder.durable("queue.pay.success")
            .withArgument("x-dead-letter-exchange", "order.dlx")
            .withArgument("x-dead-letter-routing-key", "order.dead")
            .build();
}

@Bean
Binding paySuccessBinding(Queue paySuccessQueue, DirectExchange orderExchange) {
    return BindingBuilder.bind(paySuccessQueue)
            .to(orderExchange)
            .with("order.pay.success");
}
```

消费：

```java
@RabbitListener(queues = "queue.pay.success")
public void onPaySuccess(String body, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    try {
        // ...
        channel.basicAck(tag, false);
    } catch (Exception e) {
        channel.basicNack(tag, false, false);
    }
}
```

`application.yml` 中常见配置：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
```

管理台默认：`http://localhost:15672`（用户 guest/guest，仅限本地）。

---

## 九、常见误区

| 误区 | 正解 |
|------|------|
| 消息发给队列 | 通常发给 **Exchange**，由 Binding 进队列 |
| 队列持久化 = 消息不丢 | 还要消息持久化 + Confirm 等 |
| 失败就 requeue=true | 毒消息会死循环；优先死信或有限重试 |
| 有了 MQ 就不需要幂等 | MQ 常至少一次，**必须幂等** |
| Fanout 还要精心设计 routing key | Fanout **忽略** routing key |
| 一个业务一个 Broker 连接狂开 | 复用 Connection，多开 Channel |

---

## 十、一张图串起来

```text
Producer
  │ basicPublish(exchange, routingKey, body)
  ▼
Exchange ──Binding──► Queue ──basicDeliver──► Consumer
                          │
                          ├─ Ack ──────────► 删除消息
                          ├─ Nack+requeue ► 回队列
                          └─ Nack 无 requeue + DLX ► 死信队列 ► 补偿/告警
```

---

## 十一、学习路径建议

1. 本地起 RabbitMQ，打开 15672，手动创建 Direct + 队列，用管理台发一条消息  
2. 写最小 Producer / Consumer，观察 Ack 前后 Unacked 数量变化  
3. 故意消费失败，对比 `requeue=true/false`  
4. 给队列挂上 DLX，看消息如何进死信队列  
5. 再用 Topic / Fanout 各做一个小样例  

当你能不看文档画出「交换机 → 绑定 → 队列 → 消费者 → 死信」时，接入任何业务项目都会轻松很多。

---

## 十二、小结

- **Exchange 负责路由，Queue 负责存储与投递，Binding + Routing Key 决定路径**  
- **Direct 精确、Topic 模式、Fanout 广播** —— 按场景选型  
- **消费端用手动 Ack；失败慎用无限 requeue；死信用于隔离与补偿**  
- **生产端 Confirm、消息持久化、消费端幂等** —— 可靠投递的三件套  

消息队列解决的是 **异步、解耦、削峰**；它不自动保证业务正确。业务正确仍然靠：清晰的状态机、幂等、以及失败后的补偿路径。
