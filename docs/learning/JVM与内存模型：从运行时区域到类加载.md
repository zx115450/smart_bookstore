# JVM 与内存模型：从运行时区域到类加载


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

本文把 JVM 入门里最该搞清的四块讲透：运行时数据区、垃圾回收直觉、Java 内存模型（JMM）与 happens-before、类加载与双亲委派。重点是概念模型和「为什么会出这类问题」，配合可复现的小例子；不追求背熟某一版 HotSpot 的每一个参数。

---

## 目录

1. [运行时区域](#1-运行时区域)
2. [垃圾回收 GC 入门](#2-垃圾回收-gc-入门)
3. [JMM 与 happens-before](#3-jmm-与-happens-before)
4. [类加载与双亲委派](#4-类加载与双亲委派)
5. [对照与自检](#5-对照与自检)

---

## 1. 运行时区域

JVM 规范里的运行时数据区是**概念模型**；具体 HotSpot 实现会有差异（例如方法区在 JDK 8 后以元空间形式落在本地内存）。先记住「谁私有、谁共享、溢出长什么样」。

### 1.1 程序计数器（PC Register）

- **每线程一份**
- 记录当前线程正在执行的字节码指令地址（本地方法时可为 undefined）
- 几乎不会 OOM；是线程切换后能接着跑的关键

### 1.2 Java 虚拟机栈（JVM Stack）

- **每线程一份**
- 每个方法调用压入一个**栈帧（Stack Frame）**，大致包含：
  - 局部变量表（基本类型、对象引用）
  - 操作数栈
  - 动态链接、返回地址等
- 方法结束（正常或异常）则出栈

直觉：

```text
main()
  └─ foo()
       └─ bar()   ← 当前栈顶帧
```

**栈溢出 `StackOverflowError` 常见原因：**

1. 递归过深、没有正确终止条件  
2. 单个栈帧过大（局部变量/中间计算过多），在默认栈大小下更容易顶满  

```java
public class StackOverflowDemo {
    static void f(int depth) {
        f(depth + 1); // 无限递归
    }

    public static void main(String[] args) {
        f(0); // 很快 StackOverflowError
    }
}
```

可用 `-Xss` 调整线程栈大小（过大浪费内存，过小更容易栈溢出）。另有一种「大量线程各自占栈」导致无法创建新线程的 OOM，和单线程栈溢出不同。

### 1.3 堆（Heap）

- **线程共享**
- 几乎所有对象实例、数组都在这里（逃逸分析等优化可能栈上分配，属进阶例外）
- **GC 的主战场**

常见分代视角（经典模型，便于理解）：

| 区域 | 特点 |
| --- | --- |
| 新生代（Young） | 朝生夕死，Minor GC 频繁 |
| 伊甸园区 Eden | 新对象优先分配 |
| 幸存区 Survivor | From / To，熬过 GC 的对象来回拷贝 |
| 老年代（Old） | 存活久、大对象等，Full / Major GC 更贵 |

**堆溢出 `OutOfMemoryError: Java heap space` 常见原因：**

1. 创建对象过多、缓存无界增长  
2. 内存泄漏：集合一直持有、监听器未注销、线程池 + `ThreadLocal` 未 `remove` 等  
3. `-Xmx` 设得过小  

```java
public class HeapOomDemo {
    public static void main(String[] args) {
        java.util.List<byte[]> list = new java.util.ArrayList<>();
        while (true) {
            list.add(new byte[1024 * 1024]); // 持续占住，无法回收
        }
    }
}
```

常用参数直觉：`-Xms` 初始堆，`-Xmx` 最大堆；生产常设成相等，减少运行中扩容抖动。

### 1.4 方法区 / 元空间（Metaspace）

- 逻辑上存：**类的结构信息**、运行时常量池、静态变量、方法元数据等  
- JDK 8 起 HotSpot 用**元空间**实现，多数在**本地内存**，不再固定落在堆的永久代  
- 类加载器创建大量动态类、或类未卸载导致元空间涨，可能 `OutOfMemoryError: Metaspace`

和堆的分工：对象实例在堆；「这个类长什么样」在方法区 / 元空间。

### 1.5 本地方法栈

为 `native` 方法服务，和 JVM 栈类似，也是线程私有。入门知道即可。

### 1.6 一张图串起来

```text
线程1                线程2                 共享
─────                ─────                 ────
PC                   PC
JVM 栈               JVM 栈
本地方法栈           本地方法栈
                         ╲               ╱
                          ↘  堆（对象） ↙
                          ↘ 方法区/元空间（类元数据）↙
```

### 1.7 栈溢出 vs 堆溢出

| | 栈 | 堆 |
| --- | --- | --- |
| 典型异常 | `StackOverflowError` | `OutOfMemoryError: Java heap space` |
| 典型原因 | 深递归、栈帧过大 | 对象多、泄漏、堆上限小 |
| 排查方向 | 看调用栈是否过深 | dump 堆、看支配树 / 泄漏路径 |

---

## 2. 垃圾回收 GC 入门

### 2.1 为何需要 GC

程序员主要管「new」，不管「free」。JVM 负责找出不再使用的对象并回收内存，降低泄漏与手工释放错误，但会引入停顿、CPU 开销等代价。

### 2.2 如何判断对象可回收：可达性分析

现代 HotSpot 用**可达性分析**：从一组 **GC Roots** 出发，沿着引用链能走到的对象为「存活」；不可达则可回收。

常见 GC Roots 直觉（不必背全）：

- 虚拟机栈中引用的对象（正在跑的方法的局部变量）  
- 方法区中静态属性、常量引用的对象  
- 本地方法栈中 JNI 引用  
- 被同步锁持有的对象等  

```text
GC Roots
   │
   ├─ local ref ──► A ──► B
   │
   └─ static ────► C

D 没有任何 Root 能走到 → 可回收
```

注意：仅仅「对象之间互相引用」但都到不了 Root，仍可一起被回收（解决引用计数的循环引用问题）。

### 2.3 分代假说与算法直觉

经验法则：**绝大多数对象朝生夕死；熬过几次 GC 的对象更可能继续活很久。**

| 思路 | 适用 | 直觉 |
| --- | --- | --- |
| 标记 - 复制 | 新生代 | 活对象少，拷到另一半空间，清理快 |
| 标记 - 清除 | 老年代等 | 标记后直接清，易碎片 |
| 标记 - 整理 | 老年代等 | 标记后把活对象挪紧凑，减少碎片 |

新生代常配合复制；老年代更常清除 / 整理或其变体。具体收集器会组合这些思想。

### 2.4 Minor / Major / Full 与 STW

- **Minor GC**：主要回收新生代，一般较频繁、相对快  
- **Major / Old GC**：主要针对老年代（术语因收集器而异）  
- **Full GC**：整堆（及元空间等相关区域）大规模回收，往往更慢  

**Stop-The-World（STW）**：GC 的某些阶段会暂停所有应用线程，保证不会一边改对象图一边扫。低延迟收集器（G1、ZGC、Shenandoah 等）努力缩短或并发化 STW，但不能理解为「完全无停顿」。

### 2.5 常见收集器印象（只需建立坐标）

| 收集器 | 印象 |
| --- | --- |
| Serial / Parallel | 简单或吞吐优先，适合小堆或批处理 |
| CMS（已淘汰路线） | 曾主打老年代并发标记清除，碎片与复杂度问题多 |
| G1 | 面向大堆，分区 Region，可设停顿目标 |
| ZGC / Shenandoah | 超低延迟方向，大堆上停顿更可控 |

初学先掌握：「GC 在干什么、STW 是什么、看日志里的 pause」，再按项目 JDK 版本查具体参数。

### 2.6 看 GC 日志要抓什么

打开示例（JDK 9+ 统一日志风格，具体以版本为准）：

```text
-Xlog:gc*:file=gc.log:time,uptime,level,tags
```

关注：

1. 每次 pause 多长、是否越来越频繁  
2. 年轻代 / 老年代回收前后占用  
3. 是否频繁 Full GC、是否伴随晋升失败、元空间膨胀  

工具：`jstat -gc <pid>`、VisualVM、JDK Mission Control、在线 GC 日志分析器等。

### 2.7 引用级别

| 级别 | 回收时机直觉 | 典型用途 |
| --- | --- | --- |
| 强引用 `new Object()` | 有 Root 可达就不收 | 普通业务对象 |
| 软引用 `SoftReference` | 内存将不足时倾向回收 | 内存敏感缓存 |
| 弱引用 `WeakReference` | 下次 GC 就可能收 | `WeakHashMap`、规范级缓存键 |
| 虚引用 `PhantomReference` | 不影响存活，用于回收跟踪 | 堆外资源清理等，少见 |

```java
Object strong = new Object();
java.lang.ref.WeakReference<Object> weak =
        new java.lang.ref.WeakReference<>(new Object());
// 若无其它强引用指向 WeakReference 包装的对象，GC 后 weak.get() 可能为 null
```

`ThreadLocalMap` 的 key 是弱引用：`ThreadLocal` 对象本身可被回收，但若 value 不 `remove`，仍可能挂在长生命周期线程上造成泄漏（值是强引用）。

---

## 3. JMM 与 happens-before

### 3.1 JMM 在说什么

Java 内存模型（JMM）规定：

- 多线程如何通过**主内存**与工作内存交互  
- 哪些写对哪些读**可见**  
- 哪些指令重排是允许的，哪些被禁止  

它不是某一块物理硬件，而是**语言级契约**：让编译器、CPU、缓存在遵守契约的前提下优化。

抽象图：

```text
线程 A 工作内存          主内存           线程 B 工作内存
  x' = ?        ←读/写→    x=0     ←读/写→   x' = ?
```

没有同步时，A 写了 `x=1`，B 可能很久读到 `0`，甚至看到「重排后的中间态」。

### 3.2 可见性例子

```java
public class Visibility {
    static boolean running = true; // 试改成 volatile

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(() -> {
            while (running) {
                // busy loop
            }
            System.out.println("exit");
        });
        t.start();
        Thread.sleep(100);
        running = false;
        t.join(2000);
        System.out.println("alive=" + t.isAlive());
    }
}
```

子线程可能优化成把 `running` 缓存在寄存器里，永远看不到主线程的写。加 `volatile`，或用 `synchronized` / 其它同步手段，建立可见性。

### 3.3 happens-before（先行发生）

若操作 A happens-before 操作 B，则 A 的结果对 B 可见，且 A 的顺序在 B 之前（在 JMM 意义上）。

入门只需记住常见几条（不必背全 JSR）：

1. **程序顺序**：同一线程内，前面的操作 HB 后面的操作  
2. **监视器锁**：`unlock` HB 后续对同一锁的 `lock`  
3. **volatile**：对某 `volatile` 的写 HB 后续对该变量的读  
4. **线程启动**：`Thread.start()` HB 该线程内的第一个动作  
5. **线程终止**：线程内最后一个动作 HB 其它线程检测到该线程终止（如 `join` 返回）  
6. **传递性**：A HB B 且 B HB C ⇒ A HB C  

```java
// synchronized 建立 HB
synchronized (lock) {
    shared = 42;      // 写
}                     // unlock
// 另一线程：
synchronized (lock) { // lock：能看到 shared == 42
    System.out.println(shared);
}
```

### 3.4 没同步的共享可变状态

会出现：

- 读到过期值  
- 看到对象「半初始化」（构造过程字段重排）  
- `i++` 丢失更新  

对策：锁、`volatile`（仅适合场景）、原子类、并发容器、不可变对象 + 安全发布。原则：**跨线程共享且可变，就必须有明确的同步策略。**

### 3.5 `final` 与安全发布（直觉）

正确构造完成的对象，其 `final` 字段在构造结束后对其它线程有特殊可见性保证（在满足安全发布的前提下）。这也是不可变对象在并发里更省心的原因之一。

---

## 4. 类加载与双亲委派

### 4.1 类何时需要加载

第一次主动使用某类时（例如 `new`、访问静态字段/方法、反射、主类启动等），若尚未加载，则触发类加载。

### 4.2 三个大阶段

```text
加载（Loading）→ 链接（Linking）→ 初始化（Initialization）
                 ├─ 验证
                 ├─ 准备
                 └─ 解析（可延迟）
```

| 阶段 | 做什么 |
| --- | --- |
| 加载 | 按全限定名找到字节流（`.class` / 网络 / 生成），变成方法区内的类结构，并在堆上生成 `Class` 对象 |
| 验证 | 字节码合法性、安全 |
| 准备 | 为静态字段分配内存并设**零值**（不是代码里的初始值；`static final` 编译期常量等例外） |
| 解析 | 符号引用转为直接引用（可惰性） |
| 初始化 | 执行 `<clinit>`：静态变量赋值、静态代码块 |

```java
public class InitOrder {
    static int a = 1;           // 初始化阶段赋值
    static {
        System.out.println("clinit, a=" + a);
    }
}
```

准备阶段 `a` 先是 `0`；初始化时才变成 `1` 并跑静态块。

### 4.3 类加载器层次（常见）

| 加载器 | 大致职责 |
| --- | --- |
| Bootstrap | 启动类加载器，加载核心库（如 `rt` / 模块化后的 java.base 等） |
| Extension / Platform | 扩展 / 平台类加载器 |
| Application | 应用类路径 / 模块路径上的业务类 |
| 自定义 | 框架、热部署、隔离插件等 |

注意：判断两个类是否「同一个类」，看 **全限定名 + 加载它的 ClassLoader**。同一份字节码被两个加载器加载，会得到两个不同的 `Class`。

### 4.4 双亲委派模型

流程：

1. 类加载器收到加载请求  
2. **先委派给父加载器**尝试  
3. 父加载器一路向上；只有父们都无法加载时，自己再试  

```text
请求加载 com.foo.Bar
Application → Platform → Bootstrap
                ↑ 父们找不到
Application 自己加载成功
```

**目的：**

1. **保护核心类**：避免业务 classpath 里伪造 `java.lang.String` 被优先加载  
2. **保证唯一性**：核心类由祖先加载器加载，全局一份，避免类型混乱  

```java
public class WhoLoads {
    public static void main(String[] args) {
        System.out.println(String.class.getClassLoader()); // 常为 null，表示 Bootstrap
        System.out.println(WhoLoads.class.getClassLoader()); // AppClassLoader
    }
}
```

### 4.5 何时会「打破」双亲委派（了解）

| 场景 | 原因直觉 |
| --- | --- |
| SPI（JDBC、JNDI 等） | 核心库要加载应用侧实现，需线程上下文类加载器等反向委派 |
| 热部署 / 插件隔离 | 不同模块各自加载器，互相卸载、隔离 |
| OSGi 等模块系统 | 更复杂的委派图 |

入门记住：默认双亲委派保安全与唯一；框架里出现「子加载父」时，是有意设计，不是随意破坏。

### 4.6 与 OOM 的关联

频繁动态生成类（代理、脚本、热加载）且加载器无法卸载 → 元空间上涨 → `Metaspace` OOM。排查时除堆 dump，也要关注类加载器与动态类数量。

---

## 5. 对照与自检

### 5.1 区域速查

| 区域 | 私有 / 共享 | 主要内容 | 典型问题 |
| --- | --- | --- | --- |
| 程序计数器 | 私有 | 执行位置 | 几乎无 |
| 虚拟机栈 | 私有 | 栈帧 | `StackOverflowError` |
| 堆 | 共享 | 对象 | 堆 OOM、GC 压力 |
| 方法区 / 元空间 | 共享 | 类元数据 | Metaspace OOM |

### 5.2 GC 三句话

1. 可达性分析：从 GC Roots 走不到就能收  
2. 分代：新对象易死，用复制更划算；老对象用更重的策略  
3. STW：部分阶段停全世界，低延迟收集器尽量缩短它  

### 5.3 JMM 一句话

没有 happens-before（锁 / `volatile` / 线程起停等），就不要假设「我写了你一定看见、顺序一定如你所想」。

### 5.4 双亲委派一句话

先找父亲加载，保护 `java.*` 不被篡改，并保证同类唯一。

### 5.5 自测题

1. 无限递归和 `List` 里不停 `add(new byte[1<<20])` 分别更可能触发什么错误？为什么？  
2. 两个对象互相引用，还会被 GC 吗？条件是什么？  
3. 为什么说 `volatile` 不能代替锁做 `count++`？  
4. 双亲委派如何阻止自定义 `java.lang.Integer`？  

参考答案提示：

1. 前者栈溢出；后者堆溢出（强引用一直占着）。  
2. 会，只要从 GC Roots 都不可达。  
3. `++` 是读改写三步，`volatile` 只保证单次访问可见，不保证复合原子。  
4. 加载请求会委派到 Bootstrap，由它加载真正的核心类；应用加载器轮不到用你 jar 里的假类顶替（在正常委派下）。

---

## 结语

JVM 入门不必一次吃透收集器源码。先能画出「栈私有、堆共享」，能解释可达性与 STW，能用 happens-before 解释并发可见性，能说清双亲委派的目的——就已经能把大多数线上「溢出 / 停顿 / 类冲突」问题归到正确的盒子里。之后再按 JDK 版本补 G1/ZGC 参数与工具链即可。
