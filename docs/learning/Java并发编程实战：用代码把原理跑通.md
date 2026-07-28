# Java 并发编程实战：用代码把原理跑通


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

本文按「问题 → 代码 → 结论」组织，覆盖日常开发与面试高频的 Java 并发内容：线程与中断、JMM 直觉、`synchronized` / `wait-notify`、手写阻塞队列、`volatile` 与原子类、显式锁与同步器、`ThreadLocal`、线程池、`CompletableFuture`、并发容器与死锁。目标不是背名词，而是每个点都能对应一段能跑通的例子。

---

## 目录

1. [并发要解决什么](#1-并发要解决什么)
2. [线程基础与中断](#2-线程基础与中断)
3. [内存可见性与 JMM 直觉](#3-内存可见性与-jmm-直觉)
4. [synchronized 与管程](#4-synchronized-与管程)
5. [wait / notify 与手写阻塞队列](#5-wait--notify-与手写阻塞队列)
6. [volatile：可见，但不原子](#6-volatile可见但不原子)
7. [原子类与 CAS](#7-原子类与-cas)
8. [ReentrantLock 与 Condition](#8-reentrantlock-与-condition)
9. [同步工具：Latch / Barrier / Semaphore](#9-同步工具latch--barrier--semaphore)
10. [ThreadLocal](#10-threadlocal)
11. [线程池 ThreadPoolExecutor](#11-线程池-threadpoolexecutor)
12. [CompletableFuture](#12-completablefuture)
13. [并发集合选型](#13-并发集合选型)
14. [死锁与排查](#14-死锁与排查)
15. [对照表与练习建议](#15-对照表与练习建议)

---

## 1. 并发要解决什么

多线程同时读写共享数据时，会出现三类问题：

| 问题 | 含义 | 典型症状 |
| --- | --- | --- |
| 原子性 | 一段操作不可被打断地完成 | `i++` 结果偏小 |
| 可见性 | 一个线程的写，另一个线程何时能看见 | 循环条件永远看不到更新 |
| 有序性 | 指令可能被重排 | 发布对象时读到半初始化状态 |

同步手段（锁、`volatile`、原子类、并发容器）都是在不同成本下解决这三类问题的子集。

---

## 2. 线程基础与中断

### 2.1 `start` 才创建新线程

```java
Thread t = new Thread(() ->
        System.out.println(Thread.currentThread().getName()));
t.run();   // 仍在当前线程执行
t.start(); // 新线程中执行 run()
```

### 2.2 Runnable / Callable / FutureTask

```java
Runnable r = () -> System.out.println("no result");

Callable<Integer> c = () -> {
    Thread.sleep(100);
    return 42;
};

// 直接 call()：同步，不开线程
Integer sync = c.call();

// FutureTask：既是 Runnable 又是 Future
FutureTask<Integer> ft = new FutureTask<>(c);
new Thread(ft, "worker").start();
Integer async = ft.get(); // 阻塞等待结果或异常
```

`ExecutorService.submit(callable)` 内部也是包装成可获取结果的任务，由池内线程执行。

### 2.3 线程状态：BLOCKED ≠ WAITING

| 状态 | 场景 |
| --- | --- |
| `BLOCKED` | 卡在进 `synchronized`，锁被占用 |
| `WAITING` | `Object.wait()` / `LockSupport.park()` / 无超时 `join` |
| `TIMED_WAITING` | `sleep`、带超时的 `wait` / `join` |

`wait` 会**释放锁**；`BLOCKED` 是还没拿到锁。

### 2.4 中断是协作式的

```java
public class InterruptDemo {
    public static void main(String[] args) throws Exception {
        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(200); // 可响应中断
                } catch (InterruptedException e) {
                    // sleep 抛异常时会清除中断标志，需自行恢复或退出
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("退出");
        });
        worker.start();
        Thread.sleep(500);
        worker.interrupt(); // 请求中断，不是强制杀死
        worker.join();
    }
}
```

线程池取消任务、优雅停机，都依赖「任务代码响应中断」。

---

## 3. 内存可见性与 JMM 直觉

每个线程有工作内存的抽象；共享变量在主内存。没有同步时，一个线程的写可能长时间不被另一个线程看见。

```java
public class VisibilityDemo {
    static boolean running = true; // 试改成 volatile boolean

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(() -> {
            while (running) {
                // 空转；JIT 可能把 running 缓存在寄存器
            }
            System.out.println("stopped");
        });
        t.start();
        Thread.sleep(100);
        running = false; // 子线程可能永远看不到
        t.join(1000);
        System.out.println("alive? " + t.isAlive());
    }
}
```

建立 happens-before 的常见方式：`synchronized` 解锁对加锁、`volatile` 写对读、线程 `start` / `join`、`final` 字段初始化、并发工具内部语义等。

---

## 4. synchronized 与管程

### 4.1 互斥 + 可见

```java
public class SafeCounter {
    private long value;
    private final Object lock = new Object(); // 私有 final，避免锁被外部共用

    public void increment() {
        synchronized (lock) {
            value++;
        }
    }

    public long get() {
        synchronized (lock) {
            return value;
        }
    }
}
```

同一监视器上：进入前能看到之前持有该锁的线程的释放前写入。

### 4.2 无锁时丢更新

```java
static long k = 100_000;
static long sum = 0;

// 多线程同时：
while (k > 0) {
    k--;
    sum++;
}
// sum 往往远小于 100000：判断与 ++/-- 都非原子
```

### 4.3 锁对象选择

| 写法 | 评价 |
| --- | --- |
| `private final Object lock = new Object()` | 推荐 |
| `synchronized (this)` / 同步实例方法 | 简单，但锁暴露给持有引用的外部 |
| `synchronized (Xxx.class)` | 静态全局互斥，粒度粗 |
| 字符串字面量、`Integer.valueOf` 小整数 | 禁止：可能共享同一对象 |

---

## 5. wait / notify 与手写阻塞队列

### 5.1 模板

```java
synchronized (lock) {
    while (!条件成立) {   // 必须 while，不能 if
        lock.wait();
    }
    // 修改共享状态
    lock.notifyAll();
}
```

为何用 `while`：虚假唤醒；`notifyAll` 后多个线程竞争，条件可能再次不满足。

### 5.2 完整：有界阻塞队列

```java
public class SimpleBlockingQueue<T> {
    private final Object[] items;
    private int takeIndex;
    private int putIndex;
    private int count;
    private final Object lock = new Object();

    public SimpleBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        items = new Object[capacity];
    }

    public void put(T item) throws InterruptedException {
        synchronized (lock) {
            while (count == items.length) {
                lock.wait();
            }
            items[putIndex] = item;
            putIndex = (putIndex + 1) % items.length;
            count++;
            lock.notifyAll();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        synchronized (lock) {
            while (count == 0) {
                lock.wait();
            }
            T x = (T) items[takeIndex];
            items[takeIndex] = null;
            takeIndex = (takeIndex + 1) % items.length;
            count--;
            lock.notifyAll();
            return x;
        }
    }
}
```

生产者 - 消费者：

```java
SimpleBlockingQueue<Integer> q = new SimpleBlockingQueue<>(8);

Thread producer = new Thread(() -> {
    try {
        for (int i = 0; i < 100; i++) {
            q.put(i);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
});

Thread consumer = new Thread(() -> {
    try {
        for (int i = 0; i < 100; i++) {
            System.out.println(q.take());
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
});

producer.start();
consumer.start();
```

生产请用 `ArrayBlockingQueue` / `LinkedBlockingQueue`。手写是为了理解「条件队列」。

---

## 6. volatile：可见，但不原子

```java
public class VolatileFlag {
    private volatile boolean shutdown;

    public void stop() {
        shutdown = true; // 写立刻对其他线程的读可见
    }

    public void runLoop() {
        while (!shutdown) {
            // 做短暂工作
        }
    }
}
```

```java
volatile int i = 0;
// 多线程 i++ 仍然可能丢更新：
// 读 i → 加一 → 写回，中间可交错
```

适用：状态旗标、安全发布不可变对象引用（仍要注意对象内部可变字段）。  
不适用：替代锁去做复合读写。

---

## 7. 原子类与 CAS

### 7.1 AtomicInteger

```java
AtomicInteger count = new AtomicInteger();

count.incrementAndGet();          // 等价于原子 ++i
count.compareAndSet(expect, update); // CAS：期望值匹配才更新
```

CAS 思路：读当前值 → 算新值 → 用 CPU 原子指令「仅当仍是旧值才写入」；失败则重试。无锁，但高竞争下自旋浪费 CPU。

### 7.2 高竞争计数用 LongAdder

```java
LongAdder adder = new LongAdder();
// 多线程：
adder.increment();
long total = adder.sum(); // 近似汇总（并发下可能不是绝对瞬时精确）
```

`LongAdder` 把热点分散到多个 Cell，适合统计类场景；需要精确「当前值参与 CAS」时仍用 `AtomicLong`。

### 7.3 AtomicReference 与 ABA

```java
AtomicReference<Node> head = new AtomicReference<>();
// ABA：值从 A→B→A，CAS 以为没变，实际中间状态已变
// 可用 AtomicStampedReference / AtomicMarkableReference 带版本
```

---

## 8. ReentrantLock 与 Condition

比 `synchronized` 多：可中断获取、可超时、可公平、可多个条件队列。代价是必须 `unlock`，易漏。

```java
public class BoundedBuffer<T> {
    private final Object[] items;
    private int count, putIdx, takeIdx;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }

    public void put(T x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) {
                notFull.await();
            }
            items[putIdx] = x;
            putIdx = (putIdx + 1) % items.length;
            count++;
            notEmpty.signal(); // 只叫醒「等非空」的一方
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            T x = (T) items[takeIdx];
            items[takeIdx] = null;
            takeIdx = (takeIdx + 1) % items.length;
            count--;
            notFull.signal();
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

对比第 5 节的 `notifyAll`：两个 `Condition` 避免「生产者叫醒生产者」。

```java
// 可超时、可中断获取
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try {
        // ...
    } finally {
        lock.unlock();
    }
}

lock.lockInterruptibly(); // 等待锁时可响应中断
```

---

## 9. 同步工具：Latch / Barrier / Semaphore

### 9.1 CountDownLatch：一次性大门

主线程等 N 个子任务完成：

```java
int n = 3;
CountDownLatch done = new CountDownLatch(n);
ExecutorService pool = Executors.newFixedThreadPool(n);

for (int i = 0; i < n; i++) {
    pool.execute(() -> {
        try {
            // 干活
        } finally {
            done.countDown();
        }
    });
}
done.await(); // 直到计数归零
pool.shutdown();
```

不能重置；要可循环用 `CyclicBarrier`。

### 9.2 CyclicBarrier：人齐再走，可循环

```java
CyclicBarrier barrier = new CyclicBarrier(3, () ->
        System.out.println("三人都到齐，进入下一阶段"));

Runnable worker = () -> {
    try {
        // 阶段 1
        barrier.await();
        // 阶段 2
        barrier.await();
    } catch (Exception e) {
        Thread.currentThread().interrupt();
    }
};
```

### 9.3 Semaphore：限流许可

```java
Semaphore sem = new Semaphore(10); // 最多 10 个并发

sem.acquire();
try {
    // 访问受限资源，如下游连接
} finally {
    sem.release();
}
```

---

## 10. ThreadLocal

每线程一份独立变量副本，互不干扰。

```java
public class UserContext {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void set(String user) {
        USER.set(user);
    }

    public static String get() {
        return USER.get();
    }

    public static void clear() {
        USER.remove(); // 线程池场景必须清
    }
}

// 过滤器 / 拦截器
UserContext.set("alice");
try {
    // 业务代码任意处 UserContext.get()
} finally {
    UserContext.clear();
}
```

泄漏原因：线程池线程长期存活，`ThreadLocalMap` 条目不 `remove`，value 无法回收。规范：**用完必 `remove`**。

---

## 11. 线程池 ThreadPoolExecutor

### 11.1 核心参数与执行顺序

```java
AtomicInteger idx = new AtomicInteger();
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4,                              // corePoolSize
        8,                              // maximumPoolSize
        60L, TimeUnit.SECONDS,          // 非核心空闲存活
        new ArrayBlockingQueue<>(100),  // 有界队列（关键）
        r -> new Thread(r, "biz-" + idx.getAndIncrement()),
        new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
);
```

任务提交时大致顺序：

1. 工作线程数小于 core → 建核心线程执行  
2. 否则尝试入队  
3. 队列满且线程数小于 max → 建非核心线程  
4. 仍无法接收 → 拒绝策略  

| 拒绝策略 | 行为 |
| --- | --- |
| `AbortPolicy`（默认） | 抛 `RejectedExecutionException` |
| `CallerRunsPolicy` | 调用者线程自己跑，形成反压 |
| `DiscardPolicy` | 默默丢弃 |
| `DiscardOldestPolicy` | 丢队列头再试 |

### 11.2 为何慎用 Executors 工厂

```java
Executors.newFixedThreadPool(8);  // LinkedBlockingQueue 无界 → 堆积 OOM
Executors.newCachedThreadPool();  // 最大线程 Integer.MAX_VALUE → 突发打爆
```

生产：显式 `ThreadPoolExecutor` + 有界队列 + 命名线程 + 明确拒绝策略。

### 11.3 关闭

```java
pool.shutdown(); // 不接新任务；已提交继续；方法不阻塞
if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
    pool.shutdownNow();
}
```

### 11.4 提交方式踩坑

```java
class Job implements Runnable {
    @Override
    public void run() { /* ... */ }
}

pool.execute(Job::new);   // 错误：只 new Job，不跑 run
pool.execute(new Job());  // 正确
```

---

## 12. CompletableFuture

适合组合异步结果，避免层层 `Future.get` 回调地狱。

```java
ExecutorService pool = Executors.newFixedThreadPool(4);

CompletableFuture<String> user = CompletableFuture.supplyAsync(
        () -> loadUser(1L), pool);

CompletableFuture<Integer> score = CompletableFuture.supplyAsync(
        () -> loadScore(1L), pool);

CompletableFuture<String> page = user.thenCombine(score,
        (u, s) -> u + " score=" + s);

CompletableFuture<Void> all = CompletableFuture.allOf(user, score);

page.whenComplete((r, ex) -> {
    if (ex != null) {
        ex.printStackTrace();
    } else {
        System.out.println(r);
    }
}).join();

pool.shutdown();
```

注意：

- 默认用 `ForkJoinPool.commonPool()`，业务建议传入自定义池  
- 异常会包装在 `CompletionException` 中；用 `handle` / `exceptionally` / `whenComplete` 处理  
- `get()` / `join()` 会阻塞；编排阶段尽量用链式非阻塞组合

---

## 13. 并发集合选型

| 场景 | 推荐 | 说明 |
| --- | --- | --- |
| 高并发 Map | `ConcurrentHashMap` | 分段/桶级并发；禁止 `null` 键值 |
| 读多写少 List | `CopyOnWriteArrayList` | 写时复制整表，写贵；迭代快照语义 |
| 阻塞队列 | `ArrayBlockingQueue` 等 | 生产者消费者、线程池工作队列 |
| 延迟任务 | `DelayQueue` | 按到期时间出队 |
| 并发 Set | `ConcurrentHashMap.newKeySet()` | 或 `CopyOnWriteArraySet` |

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.putIfAbsent("a", 1);
map.compute("a", (k, v) -> v == null ? 1 : v + 1); // 原子更新

CopyOnWriteArrayList<String> listeners = new CopyOnWriteArrayList<>();
listeners.add("l1");
for (String l : listeners) {
    // 遍历的是快照，中途 add 不影响本次迭代
}
```

普通 `HashMap` 多线程扩容可能导致死循环或丢数据（历史问题与数据竞争），并发场景不要共用无同步的 `HashMap`。

---

## 14. 死锁与排查

死锁四个必要条件：互斥、占有且等待、不可抢占、循环等待。

```java
Object a = new Object();
Object b = new Object();

Thread t1 = new Thread(() -> {
    synchronized (a) {
        sleep(50);
        synchronized (b) { /* ... */ }
    }
});
Thread t2 = new Thread(() -> {
    synchronized (b) {
        sleep(50);
        synchronized (a) { /* ... */ } // 与 t1 相反顺序 → 可能死锁
    }
});
```

预防：

1. 全局统一加锁顺序（例如按对象 `System.identityHashCode` 排序）  
2. `tryLock` 超时失败则释放已持有锁并重试  
3. 缩小锁范围、减少嵌套锁  

排查：`jstack <pid>` 看 `Found one Java-level deadlock`；或用 JConsole / VisualVM。

---

## 15. 对照表与练习建议

### 15.1 手段怎么选

| 需求 | 优先选择 |
| --- | --- |
| 保护一段复合临界区 | `synchronized` 或 `ReentrantLock` |
| 只做旗标 / 安全发布引用 | `volatile` |
| 单变量计数、CAS 更新 | `Atomic*` / 高竞争用 `LongAdder` |
| 等一批任务结束 | `CountDownLatch` / `CompletableFuture.allOf` |
| 多阶段汇合 | `CyclicBarrier` |
| 限流并发度 | `Semaphore` |
| 异步编排 | `CompletableFuture` + 业务线程池 |
| 生产消费解耦 | `BlockingQueue` |
| 请求上下文传递 | `ThreadLocal`（记得 `remove`） |

### 15.2 synchronized vs volatile vs Atomic

| | 原子性 | 可见性 | 典型用途 |
| --- | --- | --- | --- |
| `synchronized` | 临界区内有 | 有 | 互斥保护 |
| `volatile` | 单次读写有，`i++` 无 | 有 | 旗标 |
| `AtomicInteger` | 提供的方法有 | 有 | 无锁计数 / CAS |

### 15.3 建议动手的三道题

1. 把第 5 节队列的 `while` 改成 `if`、`notifyAll` 改成 `notify`，用多生产者多消费者压测，观察卡死或异常  
2. 用 `ReentrantLock` + 双 `Condition` 重写同一队列，对比唤醒是否更干净  
3. 自建 `ThreadPoolExecutor`，分别用有界 / 无界队列打满任务，观察内存与拒绝策略  

---

## 结语

并发没有银弹：先分清要解决的是原子性、可见性还是协作节奏，再选最轻的工具。能无锁用原子类就不要上大锁；能用并发容器就不要自己 `synchronized` 包 `HashMap`；能线程池就不要满地 `new Thread`。原理用小例子验证过之后，生产优先站在 `java.util.concurrent` 的成熟实现上。

若只记三句：

1. 共享可变状态必须有明确的同步策略  
2. 条件等待永远 `while` + 持锁 `wait` / `await`  
3. 线程池要有界、有名、有拒绝策略，并正确关闭  

延伸阅读：JUC 源码中的 `AbstractQueuedSynchronizer`（AQS，Latch / Lock 的底层骨架）。
