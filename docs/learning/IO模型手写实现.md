# I/O 模型：手写实现与面试口述


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

面试里说「手写 I/O 模型」，一般不是写内核驱动，而是：**用最小可跑代码演示阻塞 / 非阻塞 / 多路复用，并说清「谁在等、等什么」**；进阶再画 Reactor，能对应到 Java NIO / Netty。

前置直觉可先读：[Netty 前置基础](./Netty前置基础.md) 第 2～3 节。  
本文侧重：**对照表 + 手写骨架 + 口述要点**。

预计：2～4 天（跟写 + 口述自检）。

---
## 学习目标

学完应能：

1. 画出五种经典模型的「就绪 vs 拷贝」时序，并指出哪些是同步 I/O
2. 手写 BIO（一连接一线程）与 NIO Selector Echo 骨架
3. 说明 `select` / `epoll` 解决什么问题，水平触发与边缘触发差在哪
4. 白板画出 Reactor 单线程与主从，能对应 Netty Boss / Worker
5. 一句话区分：信号驱动 ≠ 异步 I/O；多路复用 ≠ 异步 I/O

---

## 1. 先分清：同步 / 异步、阻塞 / 非阻塞

### 1.1 两个正交维度

| 维度 | 问的是什么 |
| --- | --- |
| **阻塞 / 非阻塞** | 发起 `recv` 时，若数据未就绪，线程是卡住还是立刻返回 |
| **同步 / 异步** | 数据从内核拷到用户缓冲区，是**进程自己调 read 完成**，还是**内核做完再通知** |

Stevens 五模型里，前四种（阻塞、非阻塞、多路复用、信号驱动）在「拷贝」这一步仍是**同步 I/O**：进程（或线程）自己调 `read`/`recv`。只有第五种（异步 I/O）是内核拷完再通知。

### 1.2 一张图记牢

```text
阶段 A：等「内核数据就绪」（网卡 → 内核缓冲区）
阶段 B：把数据拷到用户缓冲区（内核 → 用户空间）

阻塞 I/O：        线程卡在 A+B
非阻塞 I/O：      A 立刻返回 EAGAIN，需自己轮询；就绪后再做 B
I/O 多路复用：    用 select/epoll 等一批 fd 的 A；就绪后再自己做 B
信号驱动 I/O：    SIGIO 通知 A 就绪；再自己做 B
异步 I/O：        提交请求后，A+B 都由内核做完，再通知「完成」
```

口诀：**多路复用 / 信号驱动 = 异步通知就绪 + 同步拷贝；真异步 = 完成通知。**

---

## 2. 五种模型对照（默画用）

| 模型 | 通知方式 | 拷贝谁做 | 典型 API | 面试权重 |
| --- | --- | --- | --- | --- |
| 阻塞 I/O | 无，卡在 `recv` | 进程 | `InputStream.read` | 必会 |
| 非阻塞 I/O | 自己轮询 | 进程 | `SocketChannel` 非阻塞 | 必会概念 |
| I/O 多路复用 | `select`/`epoll`/`Selector` | 进程 | Java NIO、Netty | **核心手写** |
| 信号驱动 I/O | `SIGIO` | 进程 | 较少用于网络服务 | 口述即可 |
| 异步 I/O | 完成回调 / 完成队列 | 内核 | `aio_*`、io_uring、Windows IOCP | 概念 + 对比 |

信号驱动要点（不必手写）：少了轮询，但仍是同步拷贝；信号处理可重入、信号合并丢失等问题多，生产网络服务几乎不用。

---

## 3. 手写 1：阻塞 I/O（BIO）

### 3.1 思路

```text
主线程：accept 阻塞等新连接
每来一个连接 → 新开线程（或丢线程池）→ 该线程里阻塞 read / write
```

问题：连接数 ≈ 线程数，上下文切换与内存开销大；线程在 `read` 上空等时干不了别的连接。

### 3.2 Java 骨架

```java
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BioEchoServer {
    public static void main(String[] args) throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(8080)) {
            System.out.println("BIO listening on 8080");
            while (true) {
                Socket socket = server.accept(); // 阻塞：等连接
                pool.execute(() -> handle(socket));
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) { // 阻塞：等数据
                out.println(line); // Echo
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

口述要点：

- `accept` / `read` 都会卡住当前线程
- 线程池只是「上限可控的 BIO」，模型本质没变
- 适合连接少、逻辑简单的场景；高并发长连接不合适

---

## 4. 手写 2：非阻塞轮询（了解即可）

把 socket 设成非阻塞后，`read` 没数据会立刻返回（Java 里常是 `read` 返回 0），若**没有**多路复用，就只能死循环扫所有连接 → CPU 空转。

```text
while (true) {
  for (每个连接) {
    尝试非阻塞 read
    有数据 → 处理
    没数据 → 看下一个（空转）
  }
}
```

结论：**非阻塞 Alone 不够，必须配合多路复用（或异步 I/O）。** 面试一般不要求单独手写忙等版本，但要能说出问题。

---

## 5. 手写 3：I/O 多路复用（重点）

### 5.1 思路

```text
一个线程：
  1. 把 ServerSocketChannel、各 SocketChannel 注册到 Selector
  2. selector.select() 阻塞，直到至少一个 Channel 就绪
  3. 遍历 selectedKeys：
       OP_ACCEPT → accept，新连接 register OP_READ
       OP_READ   → 读 Buffer → 业务 → 写回（或改关注 OP_WRITE）
  4. 处理完 remove key，继续 select
```

这就是「一个线程管很多连接」的原型，也是 Netty EventLoop 的简化版。

### 5.2 Java NIO Echo 骨架

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class NioEchoServer {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        server.configureBlocking(false);

        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("NIO listening on 8080");

        ByteBuffer buf = ByteBuffer.allocate(1024);

        while (true) {
            selector.select(); // 阻塞等「有人就绪」，不是空转轮询
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove(); // 必须手动移除

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                    SocketChannel client = ssc.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("accepted " + client.getRemoteAddress());
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    buf.clear();
                    int n = client.read(buf);
                    if (n < 0) { // 对端关闭
                        key.cancel();
                        client.close();
                        continue;
                    }
                    buf.flip();
                    client.write(buf); // 简化：假设一次写完；生产要处理半包写
                }
            }
        }
    }
}
```

### 5.3 跟写时必踩的坑

| 坑 | 做法 |
| --- | --- |
| `selectedKeys` 不 `remove` | 同一事件重复触发，逻辑错乱 |
| 忘记 `configureBlocking(false)` | 注册到 Selector 的 Channel 必须非阻塞 |
| `ByteBuffer` 忘了 `flip` / `clear` | 写出乱数据或读不到；见 Netty 前置基础 §3.2 |
| 在 Selector 线程里做重活 / 阻塞 RPC | 所有连接一起卡住 |
| 一次 `write` 没写完 | 应保留剩余字节，改关注 `OP_WRITE`，可写再继续 |

### 5.4 `select` / `poll` / `epoll`（口述层）

| 机制 | 特点（面试够用） |
| --- | --- |
| `select` | 有 fd 数量上限（常 1024）；每次把 fd 集合拷进内核，返回后要扫描 |
| `poll` | 链表无 1024 硬顶，仍是遍历，大规模时慢 |
| `epoll`（Linux） | fd 多时高效；支持边缘触发（ET）；Java NIO 在 Linux 上底层多用 epoll |

水平触发（LT）：缓冲区还有数据就会一直通知「可读」——好写，易漏读也能再通知。  
边缘触发（ET）：状态变化时通知一次——必须一次读到 EAGAIN，否则可能饿死；高性能场景常用，手写更易错。

Java `Selector` 对应用层多是 LT 语义；Netty 在 Linux 上可用 epoll，仍帮你屏蔽大量细节。

---

## 6. 手写 4：Reactor 模式（多路复用的工程化）

「手写 I/O 模型」进阶题，常变成：**手写 / 白板 Reactor**。

### 6.1 单线程 Reactor

```text
┌─────────────────────────────────────┐
│  Reactor 线程（一个）                 │
│  select/epoll_wait                   │
│    ├─ Accept → 注册读事件             │
│    ├─ Read   → 解码 → 业务 → 编码     │
│    └─ Write  → 发送                   │
└─────────────────────────────────────┘
```

对应上文 NIO Echo：Acceptor 与 Handler 都在同一线程。连接少、业务极轻时可用；业务一重就堵死整个事件循环。

### 6.2 主从多线程 Reactor（面试常画）

```text
Main Reactor（常 1 个）          Sub Reactors（一组）
  只负责 accept          →      每个管一批连接的读写
  把新连接分配出去              业务重活 → 再丢业务线程池（可选）
```

对应 Netty 直觉：

| Reactor 角色 | Netty |
| --- | --- |
| Main Reactor | Boss EventLoopGroup（accept） |
| Sub Reactor | Worker EventLoopGroup（读写） |
| 业务线程池 | 自己在 Handler 里另起，或默认也在 EventLoop（注意别堵） |

### 6.3 伪代码骨架（主从）

```text
// Main Reactor
while (true) {
  select(accept_fds)
  for each acceptable:
    socket = accept()
    subReactor = chooseWorker()
    subReactor.register(socket, OP_READ)
}

// Sub Reactor（每个 Worker 一个线程 + 一个 Selector）
while (true) {
  select(connection_fds)
  for each readable/writable:
    decode / encode / 轻量处理
    // 重业务：businessPool.execute(...)
}
```

口述一句：**Reactor 解决的是「事件分发与线程模型」；多路复用解决的是「一个线程如何知道谁就绪」。** 二者常一起出现，但不是同一层概念。

---

## 7. 异步 I/O（对比用，可不手写完整）

真异步：进程提交「读到这块用户缓冲区」，内核做完就绪 + 拷贝后，用回调 / 完成队列通知。

```text
aio_read(buf) ──► 进程干别的事
                 ◄── 完成通知（buf 里已有数据）
```

对比多路复用：

| | 多路复用 + 非阻塞 read | 异步 I/O |
| --- | --- | --- |
| 通知含义 | 可以读了 | 已经读完了 |
| 拷贝 | 应用自己 `read` | 内核完成 |
| Java 生态 | NIO / Netty 主流 | 旧 AIO（NIO.2）实际少用；高性能更看 io_uring 等 |

面试答法：知道定义与对比即可；「线上 Java 网络框架为什么是 Netty 而不是 AsynchronousSocketChannel」——生态、模型成熟度、历史包袱。

---

## 8. 验收练习

按顺序做，做完打勾：

1. [ ] 跑通 BIO Echo，用两个客户端同时连，观察线程行为（可用 `jstack` 或日志打线程名）
2. [ ] 跑通 NIO Selector Echo，同时连多个客户端，确认仍是少量线程
3. [ ] 故意在 NIO 的 `isReadable` 分支里 `Thread.sleep(5000)`，观察其他连接是否也被拖住
4. [ ] 白板默画：BIO / 多路复用 / 异步 三张时序，标出阶段 A、B
5. [ ] 口述 2 分钟：Reactor 主从如何对应 Netty Boss/Worker

可选加深：

- 给 NIO Echo 加上「按 `\n` 拆包」或「4 字节长度前缀」
- 处理 `write` 写不完：挂起剩余 Buffer，注册 `OP_WRITE`

---

## 9. 面试口述提纲（可直接背结构）

1. **五模型**：阻塞、非阻塞、多路复用、信号驱动、异步；前四同步拷贝，只有异步是完成通知  
2. **BIO 问题**：一连接一线程，空闲连接浪费线程  
3. **多路复用**：`select`/`epoll` 等就绪，再非阻塞读写；Java 用 `Selector`  
4. **epoll 优势**：连接多时不傻扫；ET/LT 提一句  
5. **Reactor**：事件循环 + 分发；单线程不够就主从；Netty 是工程化 Reactor  
6. **别混**：信号驱动不是异步；MQ「异步」是业务解耦，和 socket I/O 模型不同层

---

## 10. 与本仓库学习线的关系

| 文档 | 关系 |
| --- | --- |
| [Netty 前置基础](./Netty前置基础.md) | 学完本文后继续：Buffer、粘包、进 Netty |
| [Java 基础复习大纲](./Java基础复习大纲.md) | 语言 / 集合 / 并发底子 |
| [高并发](./高并发.md)、[消息队列](./消息队列.md) | 业务侧削峰与异步链路；勿与本篇 socket I/O 混为一谈 |

智慧书城当前是 Spring MVC 请求响应模型，**不必为业务接口手写 Selector**；本篇用于补网络基础与面试表达，以及日后长连接 / 网关 / 中间件方向。

---

## 相关文档

- [加深方向学习路线索引](./README.md)
- [Netty 前置基础](./Netty前置基础.md)
- [后续学习路线](../后续学习路线.md)
