# 学 Netty 前需要会的基础


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

面向准备走「中间件 / 长连接 / 网关」路线的读者。目标不是一上来啃 Netty API，而是先把 **网络、Java NIO、线程模型、协议直觉** 补齐，再进 EventLoop / Pipeline 才不空转。

对照进阶全图见：[后端进阶路线：业务 / AI vs 中间件 / Netty](../后端进阶路线-业务AI与中间件Netty.md) 路线 B。  
Java 侧 IO 提纲见：[Java 基础复习大纲](./Java基础复习大纲.md) 第 9 章。

---

## 学习目标

学完本文列出的基础后，应能：

1. 用自己的话区分 BIO / NIO / 多路复用，并说明「为何一个线程能管很多连接」
2. 解释 TCP 粘包、半包，以及应用层为何必须自己做帧定界
3. 手写或跟写一个极简 NIO Echo，分清 `ByteBuffer` 的写 / `flip` / 读 / `clear`（或 `compact`）
4. 画出 Reactor 单线程与主从多线程的直觉图，能对应到日后 Netty 的 Boss / Worker
5. 说明业务项目用 Spring MVC 足够、长连接推送 / 自定义协议才常上 Netty 的边界

预计投入：2～3 周（有并发与网络基础可压缩到 1 周）。

---

## 为什么要先打这些底

| 你在 Netty 里会看到 | 若缺基础会怎样 |
| --- | --- |
| `EventLoop` 一个线程跑很多 `Channel` | 分不清「非阻塞 + 多路复用」与「开一万个线程」 |
| `ByteBuf`、编解码器 | 仍用阻塞流思维，或搞不清粘包拆包该谁管 |
| `ChannelPipeline` | 不知道入站 / 出站顺序，Handler 乱挂 |
| 心跳、空闲检测、水位线 | 只会调 API，讲不清连接生命周期与背压 |

一句话：**Netty 是对 Reactor + 缓冲 + 管线的工程化封装**；基础没了，只会背类名。

---

## 能力地图（学完再进 Netty）

```text
① 网络与 TCP/HTTP 直觉
        ↓
② IO 模型：BIO → NIO → 多路复用（详见 [I/O 模型手写实现](./IO模型手写实现.md)）
        ↓
③ Java NIO：Channel / Buffer / Selector + 小 Demo
        ↓
④ 线程与并发：别在 EventLoop 里干重活（先有概念）
        ↓
⑤ 协议与帧：长度字段 / 分隔符 / 心跳（应用层契约）
        ↓
⑥ 设计模式直觉：Reactor、责任链（对应 Pipeline）
        ↓
      开始学 Netty 核心 API
```

---

## 1. 网络与 TCP / HTTP

### 1.1 必会概念

- **TCP**：面向连接、可靠字节流；保证到达与顺序，**不保证**「一次 `read` = 一条业务消息」
- **三次握手 / 四次挥手**：建立与释放连接的成本；大量短连接会放大握手与 TIME_WAIT 开销
- **粘包**：多次写入在接收端可能一次读出；**半包**：一次写入可能分多次读完
- **应用层协议**：必须自己定义「一条消息从哪开始、到哪结束」（长度字段、分隔符、固定长度等）
- **HTTP**：请求 / 响应、头部与 body、Keep-Alive；理解「HTTP 也是跑在 TCP 上的应用协议」即可
- **WebSocket / 长连接**：适合推送、IM；和「每次请求新建短连接的 MVC」场景不同

### 1.2 自检

- [ ] 能解释：为何「发了两条 JSON」对端可能一次读到粘在一起的字节
- [ ] 能说出至少两种拆包策略（长度字段、换行分隔）
- [ ] 能区分：端口监听、accept 新连接、已建立连接上的读写

### 1.3 不必先深挖

内核协议栈细节、拥塞控制公式、完整 HTTP/2 帧格式——进 Netty 后再按需补。

---

## 2. IO 模型：BIO → NIO → 多路复用

手写骨架、五模型时序与 Reactor 口述详见：[I/O 模型手写实现](./IO模型手写实现.md)。

### 2.1 对照表

| 模型 | 特点 | 典型问题 |
| --- | --- | --- |
| **BIO**（阻塞 IO） | 一连接一线程（或线程池），`read`/`accept` 卡住就等 | 连接多时线程爆炸、上下文切换贵 |
| **NIO**（非阻塞） | `read` 没数据立刻返回；配合 **Selector** 等「谁就绪」 | 手写 Selector 循环繁琐、易错 |
| **多路复用** | 一个线程监听大量 fd 的可读 / 可写 / 连接事件 | 现代高并发网络服务的基础（epoll 等） |
| **异步 IO（AIO）** | 操作系统完成后再回调（了解即可） | Java 里实际生态远不如 Netty + NIO 常见 |

### 2.2 要建立的直觉

```text
阻塞：线程卡在 read 上，干不了别的连接
非阻塞 + 多路复用：
  线程问 Selector「谁有事？」
  → 只处理就绪的 Channel
  → 一个线程服务大量连接
```

这就是日后 **一个 EventLoop 跑很多 Channel** 的原型。

### 2.3 自检

- [ ] 能画出「BIO 一连接一线程」与「NIO 一线程多连接」两张简图
- [ ] 能说明：非阻塞不等于「不用等」——没数据时线程去干别的或继续 select

---

## 3. Java NIO 三件套（动手必做）

对应大纲 §9.2，学 Netty 前建议**亲手跑通**，不要只看书。

### 3.1 Channel / Buffer / Selector

| 组件 | 作用 |
| --- | --- |
| `Channel` | 双向通道（如 `SocketChannel`、`ServerSocketChannel`） |
| `Buffer` | 数据容器；网络侧最常用 `ByteBuffer` |
| `Selector` | 多路复用器：注册感兴趣的事件（accept / read / write / connect） |

### 3.2 ByteBuffer 状态机（高频坑）

记住四个操作与语义：

1. **写入通道数据到 Buffer**：`channel.read(buf)`，position 前进
2. **`flip()`**：写模式 → 读模式（`limit = position`，`position = 0`）
3. **从 Buffer 读出**：`buf.get...` 或 `channel.write(buf)`
4. **`clear()`**：准备再次写入（丢弃未读标记）；若还有未读完要用 **`compact()`**

口诀：**写满或写完再 flip；读完再 clear；半包未读完用 compact。**

### 3.3 最小练习（验收）

用纯 JDK NIO（不要 Netty）完成其一即可：

1. **Echo 服务端**：客户端发什么回什么；处理粘包可用「按行」先简化
2. **多客户端**：用一个 Selector 循环同时服务多个连接

验收标准：能向别人讲清「SelectionKey 就绪 → 读入 Buffer → flip → 写出」的一步流程。

### 3.4 自检

- [ ] 不看文档能说出 `flip` / `clear` / `compact` 区别
- [ ] 知道 `ServerSocketChannel` 负责 accept，`SocketChannel` 负责读写
- [ ] 理解：业务线程里若再做阻塞调用，会堵死整条「多路复用」优势

---

## 4. 线程与并发（够用即可）

不必先精通 JMM 全书，但下面几条与 Netty **强相关**：

- **线程池**：提交任务、队列积压、拒绝策略的基本概念
- **别在 IO 线程做重活**：耗时计算、同步远程调用会拖住整条 EventLoop 上的所有连接
- **线程安全直觉**：共享可变状态需要同步；日后 Netty 约定「同一 Channel 的事件在同一 EventLoop 串行」，可减少部分加锁，但跨线程递交仍要小心
- **`volatile` / 可见性**：理解「旗标、发布」级别即可，深入 CAS 可后补

对应复习：[Java 基础复习大纲](./Java基础复习大纲.md) 第 11 章（挑线程、线程池、锁基础即可）。

### 自检

- [ ] 能举例：在「连接可读」回调里同步调远程 HTTP 会怎样
- [ ] 知道业务处理应丢到业务线程池，IO 线程尽快返回

---

## 5. 协议与帧（应用层契约）

Netty 再强也解决不了「你没定义协议」——它只是帮你挂解码器。

### 5.1 必会

- **消息边界**：长度字段（前 4 字节长度 + body）、分隔符（`\n`）、固定长度
- **编解码**：字节 ↔ 业务对象（JSON / Protobuf / 自定义二进制）
- **心跳**：探测死连接；配合空闲超时踢连接
- **请求 / 响应关联**：RPC 需要 requestId，否则异步响应对不上调用方

### 5.2 与 Netty 的对应（先混个眼熟）

| 基础问题 | 日后常用手段 |
| --- | --- |
| 粘包拆包 | `LengthFieldBasedFrameDecoder`、`DelimiterBasedFrameDecoder` |
| 编解码 | `ByteToMessageDecoder`、`MessageToByteEncoder` |
| 心跳空闲 | `IdleStateHandler` + 自定义 ping/pong |
| 管道处理 | `ChannelPipeline` 里按序加 Handler |

现在不必背类名，但要能说清**问题本身**。

### 自检

- [ ] 能设计一个「4 字节大端长度 + UTF-8 文本」的迷你协议并说明为何能拆包
- [ ] 能说明心跳包与业务包如何区分（魔数、类型字段等）

---

## 6. 设计模式直觉（点到为止）

| 模式 | 在网络框架里的样子 |
| --- | --- |
| **Reactor** | 事件循环：等事件 → 分发 → 处理；单线程 / 主从多线程变体 |
| **责任链** | 入站 / 出站 Handler 串成 Pipeline，逐个处理或传递 |
| **池化** | 缓冲、线程、对象复用，降低分配与 GC 压力（日后 `ByteBuf` 池化） |

能白板画「Acceptor + Reactor 循环」即可，不必背 GoF 全文。

---

## 7. 工具与环境

- JDK 17+（与本仓库一致即可）；会用 Maven / Gradle 拉依赖
- 会用 `telnet` / `nc`、或写个小客户端发字节做联调
- 可选：Wireshark / tcpdump 抓包看 TCP 流（加深粘包直觉）
- 会看线程 dump、简单日志（日后查「EventLoop 堵住了」很有用）

---

## 建议学习顺序（2～3 周）

| 阶段 | 天数 | 做什么 | 产出 |
| --- | --- | --- | --- |
| W1-a | 2～3 天 | TCP 粘包半包 + HTTP Keep-Alive；画图 | 一页「字节流 vs 消息」说明 |
| W1-b | 3～4 天 | BIO 小服务 vs NIO Selector Echo | 可运行的 NIO Echo + 注释 |
| W2-a | 2 天 | Buffer 状态机练熟；多客户端压一下 | 能口述 flip/clear |
| W2-b | 2～3 天 | 自定义长度协议（仍可用纯 NIO 或极简代码） | 协议说明 + 拆包正确 |
| W2-c | 1 天 | 读路线 B 地图，对照 Netty 名词表 | 知道下一步学哪些类 |

然后进入：[后端进阶路线](../后端进阶路线-业务AI与中间件Netty.md) **B.4 阶段 2**（EventLoop / Pipeline / ByteBuf）。

---

## 与 Netty 核心概念的映射（预习表）

| 基础里已有的概念 | Netty 中的对应 |
| --- | --- |
| Selector 循环 + 非阻塞 Channel | `EventLoop` + `Channel` |
| 主线程 accept、工作线程读写（手写时的拆分） | `Boss` / `Worker` `EventLoopGroup` |
| 读字节 → 解出消息 → 业务 → 再写成字节 | `ChannelPipeline` Inbound / Outbound |
| `ByteBuffer` | `ByteBuf`（引用计数、池化，更易用也更易泄漏） |
| 自己写拆包逻辑 | Frame Decoder 系列 |
| 「IO 线程别阻塞」 | 业务丢到额外 `EventExecutorGroup` / 业务池 |

---

## 总自检清单

- [ ] 能区分 BIO / NIO / 多路复用，并画出 Reactor 直觉图
- [ ] 能解释粘包、半包，并给出一种应用层定界方案
- [ ] 能独立完成或精读一套 NIO Echo，分清 Buffer 读写切换
- [ ] 知道为何重逻辑不能塞在 IO 事件回调里
- [ ] 能说明 Spring MVC 短请求 与 Netty 长连接 / 自定义协议 的适用边界
- [ ] （加分）读过一次「主从 Reactor」描述，能对应 Boss / Worker

全部勾选后，再打开 Netty 官方 examples 或《Netty 实战》，效率会高很多。

---

## 推荐资源（打基础阶段）

| 类型 | 建议 |
| --- | --- |
| 官方 | [Oracle NIO 教程](https://docs.oracle.com/javase/tutorial/essential/io/fileio.html)（文件 NIO.2）；网络侧以 Channel/Buffer 文档 + 自己写 Demo 为主 |
| 书籍（择一） | 《Java NIO》概念章节，或任意体系化「网络编程基础」章节 |
| 进阶全图 | [后端进阶路线 B](../后端进阶路线-业务AI与中间件Netty.md) |
| Netty 本体（后置） | [netty/netty](https://github.com/netty/netty) examples；《Netty 实战》 |

---

## 相关文档

- [后端进阶路线：业务 / AI vs 中间件 / Netty](../后端进阶路线-业务AI与中间件Netty.md)
- [Java 基础复习大纲](./Java基础复习大纲.md)（§9 IO 与 NIO、§11 并发）
- [学习路线目录](./README.md)
- [后续学习路线](../后续学习路线.md)
