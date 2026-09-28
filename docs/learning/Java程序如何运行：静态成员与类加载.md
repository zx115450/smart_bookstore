# Java 程序如何运行：静态成员与类加载


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：[JVM 与内存模型](./JVM与内存模型：从运行时区域到类加载.md) · [Spring Boot 底层架构](./SpringBoot框架底层架构学习博客.md)

本文用一条主线回答：从 `java Main` 按下回车，到 `static` 字段 / 方法 / 静态内部类真正执行，中间发生了什么。目标是建立「加载 ≠ 执行」「类级数据 vs 实例数据」的直觉，方便后续读 Spring Boot 自动配置里的 `static class XxxConfiguration`。

---

## 目录

1. [一条启动链路](#1-一条启动链路)
2. [类加载在说什么](#2-类加载在说什么)
3. [static 字段与静态代码块](#3-static-字段与静态代码块)
4. [静态方法怎么执行](#4-静态方法怎么执行)
5. [静态内部类：单独的一份 class](#5-静态内部类单独的一份-class)
6. [和 Spring 自动配置的关系](#6-和-spring-自动配置的关系)
7. [常见误解对照](#7-常见误解对照)
8. [自检清单](#8-自检清单)

---

## 1. 一条启动链路

### 1.1 从源码到能跑

```text
Xxx.java  ──javac──►  Xxx.class（字节码）
                              │
                              ▼
                     java 命令启动 JVM
                              │
                              ▼
                     找到并加载主类
                              │
                              ▼
                     调用 public static void main(String[] args)
```

要点：

1. `.java` 不是直接执行的脚本，先变成 `.class`
2. JVM 按类名去加载字节码，再解释执行或 JIT 编译成本地代码
3. 入口约定是：某个类上的 `public static void main(String[] args)`

### 1.2 为什么入口必须是 `static`

`main` 是程序起点，此时还没有任何「由你 new 出来的对象」。  
JVM 只能通过 **类名 + 静态方法** 直接调用，不能先依赖实例。

直觉：

```text
还没有 new Main()
    ↓
只能 Main.main(args)   ← 静态方法，挂在「类」上
```

若写成实例方法，JVM 会面临「先有鸡还是先有蛋」：没有实例就调不了 `main`，没有 `main` 又造不出业务入口。

### 1.3 启动后线程在干什么

以最简单程序为例：

```java
public class Demo {
    public static void main(String[] args) {
        System.out.println("hi");
    }
}
```

主线程大致路径：

```text
加载 Demo
  → 链接、初始化 Demo（见下文）
  → 调用 Demo.main
       → 压栈帧、执行字节码
       → println
       → main 返回，主线程结束（若无其它非守护线程，进程退出）
```

---

## 2. 类加载在说什么

类不是「写在源码里就永远存在于内存」。某个类第一次被 **主动使用** 时，才会走完整生命周期中的关键几步。

### 2.1 生命周期简图

```text
加载 Loading
  → 验证 Verification
  → 准备 Preparation      （为 static 字段分配内存、设默认值）
  → 解析 Resolution      （符号引用 → 直接引用，可延迟）
  → 初始化 Initialization （执行 <clinit>：静态赋值 + static {}）
  → 使用
  → 卸载（类加载器满足条件时，应用里较少直接关心）
```

常说的「类加载」口语里经常把上面整段都笼统带过；精确区分时：

| 阶段 | 和 static 最相关的事 |
| --- | --- |
| 加载 | 读 `.class`，生成 `Class` 对象 |
| 准备 | `static int x` 先变成 `0`，`static Object o` 先变成 `null` |
| 初始化 | 才执行 `static int x = 1` 和 `static { ... }` |

### 2.2 什么会触发「初始化」

常见主动使用（会触发初始化，若尚未初始化）：

1. `new` 该类
2. 读写该类的 **静态字段**（编译期常量除外，见下）
3. 调用该类的 **静态方法**
4. 反射：`Class.forName("...")`（默认会初始化）
5. 启动时的主类
6. 初始化子类时，会先初始化父类

**不会**仅仅因为「同文件里写了内部类」就初始化内部类；内部类是另一份 `.class`，有自己的触发条件。

### 2.3 准备 vs 初始化：一个例子

```java
public class ReadyVsInit {
    static int a = 1;
    static int b;

    static {
        b = 2;
        System.out.println("clinit runs");
    }
}
```

直觉顺序：

```text
准备阶段：a=0, b=0（默认值）
初始化：执行 <clinit>
         a=1；再跑 static 块，b=2；打印 clinit runs
```

`<clinit>` 由编译器生成，内容大致是：所有静态赋值语句 + 所有 `static {}`，按源码顺序合并。

### 2.4 编译期常量的特殊点

```java
public class Const {
    public static final int N = 42; // 基本类型编译期常量
}

public class Use {
    public static void main(String[] args) {
        System.out.println(Const.N); // 可能根本不触发 Const 初始化
    }
}
```

对 `static final` 的基本类型 / String 等编译期常量，编译器常把字面量直接打进调用方字节码。读 `Const.N` 时，**不一定**加载或初始化 `Const`。  
这和「普通 static 字段一访问就初始化」不同，面试和排查「怎么没进 static 块」时很常见。

---

## 3. `static` 字段与静态代码块

### 3.1 存在哪里、属于谁

| 概念 | 属于 | 生命周期直觉 |
| --- | --- | --- |
| 实例字段 | 每个对象一份 | 随对象创建 / 被 GC |
| 静态字段 | 类一份（与具体对象无关） | 随类初始化出现，类卸载前一直在 |

静态字段不在「某个 `this`」里，访问写法是 `类名.字段`，也可以在同类里省略类名。

### 3.2 静态代码块执行几次

```java
public class Once {
    static {
        System.out.println("static block");
    }

    public static void hello() {
        System.out.println("hello");
    }
}
```

```java
Once.hello();
Once.hello();
```

输出通常是：

```text
static block
hello
hello
```

`static {}` 在该类 **初始化阶段执行一次**，不是每次调静态方法都执行。

### 3.3 父类优先

```java
class Parent {
    static { System.out.println("Parent clinit"); }
}

class Child extends Parent {
    static { System.out.println("Child clinit"); }

    public static void main(String[] args) {
        new Child();
    }
}
```

典型输出：

```text
Parent clinit
Child clinit
```

初始化子类前，先确保父类已初始化。

### 3.4 和实例初始化块对比

```java
public class Both {
    static { System.out.println("static"); }   // 类初始化一次

    { System.out.println("instance"); }        // 每次 new 都执行

    Both() { System.out.println("ctor"); }

    public static void main(String[] args) {
        new Both();
        new Both();
    }
}
```

典型输出：

```text
static
instance
ctor
instance
ctor
```

---

## 4. 静态方法怎么执行

### 4.1 调用不经过实例

```java
public class MathUtil {
    public static int add(int a, int b) {
        return a + b;
    }
}

int s = MathUtil.add(1, 2); // 不需要 new MathUtil()
```

字节码层面常见是 `invokestatic`：解析到具体类的静态方法后直接调用。

### 4.2 静态方法里没有 `this`

```java
public class Wrong {
    int x;

    static void f() {
        // System.out.println(x); // 编译错误：不能直接访问实例字段
    }
}
```

原因：调用时可能根本没有对象。若必须碰实例状态，只能：

1. 改成实例方法；或
2. 静态方法接收一个对象参数，再通过参数访问

### 4.3 静态方法可以被隐藏，不是多态重写

```java
class A {
    static void show() { System.out.println("A"); }
}

class B extends A {
    static void show() { System.out.println("B"); }
}

A ref = new B();
ref.show(); // 输出 A（看引用编译类型），不是运行时多态
```

实例方法才是虚方法分派（`invokevirtual` / 接口分派）。静态方法按 **编译期类型** 绑定，子类同名静态方法是 **隐藏（hide）**，不是 override。

### 4.4 和「类初始化」的关系

第一次调用某类的静态方法时，若该类尚未初始化，会先跑完 `<clinit>`，再进入该方法。

```text
MathUtil.add(...)
   ↓ 若 MathUtil 未初始化
先执行 MathUtil 的 static 字段赋值 / static {}
   ↓
再执行 add 方法体
```

---

## 5. 静态内部类：单独的一份 class

### 5.1 编译产物

```java
public class Outer {
    static int outerStatic = 1;
    int outerInstance = 2;

    static class Nested {
        void f() {
            System.out.println(outerStatic); // OK
            // System.out.println(outerInstance); // 不行：没有外部实例
        }
    }

    class Inner {
        void f() {
            System.out.println(outerInstance); // OK：持有外部类引用
        }
    }
}
```

编译后大致是：

```text
Outer.class
Outer$Nested.class   ← 静态嵌套类
Outer$Inner.class    ← 普通内部类
```

三者是 **三个独立类型**，各有自己的加载与初始化时机。

### 5.2 加载时机：外层 ≠ 内层

```java
public class Outer2 {
    static {
        System.out.println("Outer2 clinit");
    }

    static class Nested {
        static {
            System.out.println("Nested clinit");
        }

        static void hello() {
            System.out.println("Nested.hello");
        }
    }

    public static void main(String[] args) {
        System.out.println("main start");
        Nested.hello();
    }
}
```

一种典型输出：

```text
Outer2 clinit
main start
Nested clinit
Nested.hello
```

说明：

1. 启动主类会初始化 `Outer2`
2. **不会**因此自动初始化 `Nested`
3. 第一次主动使用 `Nested`（这里是调静态方法）才初始化 `Nested`

这是理解 Spring 里「外层自动配置类 + 内层 `static class XxxConfiguration`」的 JVM 基础。

### 5.3 `static` 嵌套类 vs 普通内部类

| | `static class Nested` | 非 static `class Inner` |
| --- | --- | --- |
| 是否依赖外部实例 | 不需要 | 需要（隐含 `Outer.this`） |
| 创建写法 | `new Outer.Nested()` | 先有 `outer`，再 `outer.new Inner()` |
| 访问外部实例字段 | 不能直接访问 | 可以 |
| 常见用途 | 工具分组、Builder、配置分组 | 真正需要绑在某个外部对象上的逻辑 |

自动配置、工具类嵌套，几乎一律用 **`static` 嵌套类**，避免无意义的外部实例依赖。

### 5.4 静态内部类里的 `static` 成员

静态嵌套类本身就是普通类（只是名字嵌在外层命名空间里），因此可以有自己的：

- 静态字段 / 静态方法 / `static {}`
- 实例字段 / 实例方法

它们的加载与执行规则，和顶级类相同，只是触发条件变成「第一次主动使用这个嵌套类」。

---

## 6. 和 Spring 自动配置的关系

Spring Boot 常见写法：

```java
@AutoConfiguration
public class MyAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SomeService.class)
    static class SomeServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SomeService someService() {
            return new SomeService();
        }
    }
}
```

用本文的话翻译：

| 现象 | 解释 |
| --- | --- |
| 套 `static class` | 内层是独立 `.class`，可单独加载 / 单独加条件 |
| 条件写在内层 | classpath 没有 `SomeService` 时，可跳过这一组配置 |
| 外层先被导入 | 不等于内层立刻初始化；Spring 还会先做条件评估 |
| `proxyBeanMethods = false` | 配置类不需要 CGLIB 代理去拦截 `@Bean` 互调，启动更轻 |

更完整的 Boot 启动与自动配置链路，见 [Spring Boot 框架底层架构学习博客](./SpringBoot框架底层架构学习博客.md)。  
类加载器双亲委派、运行时区域等，见 [JVM 与内存模型](./JVM与内存模型：从运行时区域到类加载.md)。

---

## 7. 常见误解对照

| 误解 | 更准确的说法 |
| --- | --- |
| 类一编译就进内存 | 一般是首次主动使用时才加载 / 初始化 |
| `static {}` 每次调静态方法都跑 | 每个类初始化阶段最多跑一次 |
| 静态方法是「全局函数所以更快所以更好」 | 只是不绑定实例；滥用会破坏面向对象与可测试性 |
| 加载外层类等于加载静态内部类 | 否；内部类是另一份 class，另算触发条件 |
| 子类静态方法 override 父类静态方法 | 是否定；那是隐藏，没有实例多态 |
| `static` 字段在栈上 | 不在线程栈帧里当局部变量；类级数据，与堆 / 方法区（元空间）等实现相关，先记「类一份」即可 |
| Spring 套静态类是为了「静态更快」 | 主要是分组条件与独立加载，不是性能口号 |

---

## 8. 自检清单

1. 能画出：`javac` → `.class` → JVM 加载主类 → 调 `main` 的链路  
2. 能区分：准备阶段的默认值 vs 初始化阶段的 `<clinit>`  
3. 知道三种会触发初始化的主动使用：`new`、访问静态字段、调用静态方法（常量特殊）  
4. 能说明：静态方法为何没有 `this`，以及为何 `main` 必须是 static  
5. 能说明：`Outer.class` 与 `Outer$Nested.class` 是两份类型，初始化时机可分离  
6. 能看懂自动配置里 `static class XxxConfiguration` 在 JVM 层面在干什么  

建议亲手跑第 3、5 节的小例子，对照控制台输出，比只背定义更牢。

---

## 附录：最小实验脚本（可选）

把下面存成 `LoadOrderDemo.java`，用 `javac` / `java` 跑一遍，观察打印顺序。

```java
public class LoadOrderDemo {
    static {
        System.out.println("1) LoadOrderDemo clinit");
    }

    static class Nested {
        static {
            System.out.println("3) Nested clinit");
        }

        static void touch() {
            System.out.println("4) Nested.touch");
        }
    }

    public static void main(String[] args) {
        System.out.println("2) main");
        Nested.touch();
        Nested.touch(); // 不会再次 Nested clinit
    }
}
```

期望顺序：`1 → 2 → 3 → 4 → 4`。
