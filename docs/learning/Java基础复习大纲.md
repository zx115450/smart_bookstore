# Java 基础复习大纲（详解版）


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

面向后端 / 面试 / 回炉的系统性复习材料。每个知识点附简要讲解与常见坑；建议配合官方文档与小 demo 动手验证。

适用版本：以 **Java 17 / 21 LTS** 为主，旧版差异单独注明。

预计通读 + 查漏补缺：3～5 周（每天 1～2 小时）。

---

## 目录速览

1. [语言与运行环境](#1-语言与运行环境)
2. [类型系统与运算符](#2-类型系统与运算符)
3. [流程控制与方法](#3-流程控制与方法)
4. [面向对象核心](#4-面向对象核心)
5. [常用类与 API](#5-常用类与-api)
6. [异常与断言](#6-异常与断言)
7. [泛型](#7-泛型)
8. [集合框架](#8-集合框架)
9. [IO 与 NIO](#9-io-与-nio)
10. [反射、注解与枚举](#10-反射注解与枚举)
11. [并发编程基础](#11-并发编程基础)
12. [JVM 与内存模型入门](#12-jvm-与内存模型入门)
13. [Java 8+ 现代特性](#13-java-8-现代特性)
14. [模块化、工具链与工程实践](#14-模块化工具链与工程实践)
15. [总自检清单](#15-总自检清单)

---

## 1. 语言与运行环境

### 1.1 基本概念

**Java 是什么、如何跨平台**  
Java 源码先被编译成与平台无关的字节码（`.class`），再由各操作系统上的 JVM 解释或 JIT 编译执行。所谓「一次编写，到处运行」，前提是目标环境安装了兼容的 JVM；不是把 `.java` 直接当脚本跑，也不是生成单一原生可执行文件（除非用 GraalVM native-image 等另途）。

**JDK / JRE / JVM**  
- JVM：只负责加载并执行字节码。  
- JRE：JVM + 运行时类库（可运行已编译程序）。  
- JDK：JRE + 编译器、调试工具等（开发必备）。  
现代发行版常直接提供 JDK；不必纠结「只要 JRE」的老说法。

**常用命令**  
- `javac`：源码 → 字节码。  
- `java`：启动 JVM 运行主类。  
- `jar`：打包 / 解压 jar。  
- `javadoc`：从文档注释生成 API 文档。

**源文件与类**  
一个 `.java` 里可以有多个类，但最多一个 `public` 顶层类，且 public 类名必须与文件名一致。包名应按目录层级存放（如 `com.example.app` 对应 `com/example/app/`），否则在严格工具链下会混乱。

**main 方法**  
入口通常是 `public static void main(String[] args)`。`static` 使 JVM 无需先 new 对象；`args` 接收命令行参数。缺 `public`/`static` 或改返回类型，一般不能作为标准入口。

**classpath 与 module-path**  
classpath 告诉 JVM 到哪里找 `.class` 与 jar。Java 9+ 模块系统另有 module-path。Maven/Gradle 会帮你拼路径；手写 `java -cp` 时漏 jar 是常见「找不到类」原因。

### 1.2 程序结构

**package 与 import**  
`package` 声明命名空间，避免类名冲突。`import` 只是写代码时的短名便利，不负责「加载依赖」——真正找类靠 classpath。`import static` 可静态导入常量或静态方法，适度使用；满屏静态导入会降低可读性。

**注释**  
- `//`、`/* */`：给人看，编译器忽略。  
- `/** */`：Javadoc，可生成文档；常用 `@param` `@return` `@throws` `@since`。  
对外 API 建议写清契约；实现细节不必堆在 Javadoc 里。

**命名习惯**  
类：大驼峰；方法/变量：小驼峰；常量：全大写加下划线；包名：全小写。见名知意优于无意义缩写。

**部分关键字位置感**  
- `volatile` / `synchronized`：并发可见性与互斥。  
- `transient`：默认序列化时跳过该字段。  
- `native`：方法由本地代码实现（JNI）。  
- `strictfp`：限制浮点运算更严格的可复现性（了解即可）。

### 1.3 复习自检

- [ ] 画出「源码 → 字节码 → JVM 执行」并说明跨平台依赖什么
- [ ] 区分 JDK 与 JVM
- [ ] 说明 `PATH` 里配的是可执行文件目录，不等于 classpath

---

## 2. 类型系统与运算符

### 2.1 基本类型（primitive）

Java 有八种基本类型：`byte` `short` `int` `long`（整数）、`float` `double`（浮点）、`char`（UTF-16 代码单元）、`boolean`。

**默认值**  
成员变量（字段）有默认值：数字 0、`char` 为 `\u0000`、`boolean` 为 `false`、引用为 `null`。局部变量没有默认值，使用前必须赋值，否则编译失败。

**字面量**  
整数默认 `int`；长整型加 `L`。十六进制 `0x`、二进制 `0b`。浮点默认 `double`；`float` 加 `f`。字符用单引号；字符串是引用类型，用双引号。

**运算中的类型提升**  
较小整数运算常提升到 `int`；有 `long`/`float`/`double` 参与则向更「宽」的类型靠。强制窄化可能丢精度或溢出，需自己负责。

**注意**  
`boolean` 不能与整数互转（与 C 不同）。`char` 可参与算术，实质是无符号数值。浮点有精度误差，金额应用 `BigDecimal`。

### 2.2 引用类型

类、接口、数组、枚举、注解、`record` 都属于引用类型。变量里存的是引用（可理解为指向堆对象的句柄），不是对象本身。多个引用可指向同一对象；赋 `null` 表示不指向任何对象。

**空指针**  
对 `null` 解引用会 `NullPointerException`。常见于未初始化对象、方法返回 null、自动拆箱遇到 null（如 `Integer x=null; int y=x;`）。

**装箱与拆箱**  
基本类型与包装类（`Integer` 等）可自动转换。`Integer` 对 **-128～127** 常有缓存，该范围内 `==` 可能为 true，范围外常为 false——语义比较应一律用 `equals`。频繁装箱会产生额外对象，热路径需注意。

### 2.3 运算符与表达式

**短路逻辑**  
`&&` `||` 短路：左边已能定结果则不算右边。`&` `|` 对 boolean 也算两边（一般少用于 boolean）。利用短路可写 `obj != null && obj.method()`。

**位运算**  
`<<` `>>` `>>>`（无符号右移）、`& | ^ ~`。可用于权限掩码、奇偶判断等；日常业务代码用得少，算法/底层更多见。

**自增**  
`i++` 先用后加；`++i` 先加后用。在复杂表达式里易混，宁可拆成多行。

**instanceof**  
判断运行时类型。Java 16+ 支持模式匹配：`if (o instanceof String s) { ... }`，少一次强制转换。

### 2.4 复习自检

- [ ] 解释 `Integer` 缓存导致 `==` 的坑
- [ ] 说明为何 `0.1+0.2` 可能不等于 `0.3`
- [ ] 说清局部变量为何没有默认值

---

## 3. 流程控制与方法

### 3.1 分支与循环

**if / switch**  
`switch` 支持 `byte/short/char/int` 及其包装类、`String`、`enum`。传统 `case` 不要忘记 `break`，否则会穿透。现代箭头语法 `case X ->` 与 `yield` 可避免穿透并用于表达式。对 `null` 做 `switch` 会 NPE（需先判空）。

**循环**  
`for`、增强 for（只读遍历语义更清晰）、`while`、`do-while`。增强 for 底层是迭代器，遍历中直接结构修改集合可能 `ConcurrentModificationException`。标签 `break`/`continue` 能跳出外层循环，但可读性差，优先重构。

### 3.2 方法

**值传递**  
Java 只有按值传递：基本类型拷贝值；引用类型拷贝引用。方法内 `x = new Something()` 改的是局部副本，不影响调用方引用；但通过引用改对象内部字段，调用方能看见。

**重载**  
同名不同参数列表（个数、类型、顺序）。返回类型不同不算重载。可变参数 `T...` 本质是数组，且只能是最后一个参数；与重载组合时可能歧义。

**递归**  
每层调用占栈帧，过深导致 `StackOverflowError`。Java 不保证尾递归优化，深度递归要改成迭代或自管栈。

### 3.3 数组

数组长度固定，`length` 是字段不是方法。元素类型可以是基本类型或引用。多维数组实质是「数组的数组」，各行长度可以不同。

**Arrays 工具**  
`sort`、`binarySearch`（需有序）、`equals`/`deepEquals`、`copyOf`。`Arrays.asList` 得到的是定长列表，不能 `add`/`remove`（会抛异常）；若需可变列表应 `new ArrayList<>(Arrays.asList(...))`。

### 3.4 复习自检

- [ ] 用例子说明「改对象字段」与「重绑定引用」的区别
- [ ] 说明 `Arrays.asList` 的限制
- [ ] 解释增强 for 中删除元素为何危险

---

## 4. 面向对象核心

### 4.1 封装

把数据与操作数据的方法放在类中，并用访问控制隐藏实现。`private` 仅类内；默认包内；`protected` 包内 + 子类；`public` 全局。对外暴露稳定方法，内部字段可改结构而不影响调用方。

**构造器**  
与类同名、无返回类型。可重载。`this(...)` 调本类其它构造器，`super(...)` 调父类，且必须在构造器第一句。字段初始化顺序要心里有数：默认值 → 显式初始化 / 初始化块 → 构造器体。

**static**  
属于类而非实例。静态方法不能直接访问实例成员。静态变量在类加载后存在，注意线程安全与全局可变状态。

**不可变对象思路**  
字段 `private final`、无 setter、构造时完整赋值、可变成员做防御性拷贝。好处：天生较易线程安全、可放心共享。

### 4.2 继承

`extends` 单继承，所有类最终继承 `Object`。子类继承父类可见成员，可增加字段/方法，可重写实例方法。

**重写规则**  
方法名与参数相同；返回类型可协变；访问权限不能更窄；不能抛出更宽的受检异常。用 `@Override` 让编译器帮你检查签名。

**初始化顺序（常见考点）**  
父类静态 → 子类静态 → 父类实例初始化与构造器 → 子类实例初始化与构造器。静态只在类首次初始化时做一次。

**final**  
类不可继承；方法不可重写；变量不可再赋值（引用 final 不代表对象不可变）。

### 4.3 多态

父类引用指向子类对象时，调用**可重写的实例方法**看运行时类型（动态绑定）。静态方法、私有方法、字段不走多态：看编译时类型。

**抽象类**  
可含抽象方法与已实现方法、字段、构造器。不能实例化。适合「是一种」且有共享代码的家族。

**接口**  
更侧重能力契约。可多实现。Java 8+ 可有 `default`/`static` 方法；冲突时实现类必须显式选择。函数式接口仅一个抽象方法，可供 Lambda 使用。

**组合优于继承**  
继承强耦合父类实现；若只是复用功能，常更适合持有一个组件对象（组合）。

### 4.4 Object 契约

**equals / hashCode**  
`equals` 相等的对象必须有相同 `hashCode`（否则在 `HashMap`/`HashSet` 中会丢数据或找不到）。`equals` 应自反、对称、传递、一致，对 `null` 返回 false。只重写一个是常见 bug。

**toString**  
便于日志与调试；不要依赖其格式做业务解析。

### 4.5 内部类

成员内部类持有外部类引用；静态嵌套类不持有。局部/匿名内部类捕获的局部变量须是 final 或实质 final。Lambda 在很多场景可替代匿名类。

### 4.6 复习自检

- [ ] 默画父子类静态块与构造器顺序
- [ ] 说明字段为何没有多态
- [ ] 解释 equals/hashCode 契约被破坏时的后果

---

## 5. 常用类与 API

### 5.1 字符串与文本

`String` 不可变：任何「修改」都产生新对象（或编译器优化后的等价物）。字符串常量池可让字面量复用。拼接大量字符串在循环里用 `StringBuilder`；`StringBuffer` 带同步，一般业务用 Builder 即可。

常用：`isEmpty`/`isBlank`、`strip`、`split`（注意正则特殊字符）、`formatted`（Java 15+）、`repeat`。编码问题：字节与字符串转换必须明确 `Charset`（推荐 UTF-8），否则依赖平台默认编码易乱码。

### 5.2 数学、精度与随机

`BigDecimal` 用字符串或整数构造更安全，避免 `new BigDecimal(0.1)` 带入 double 误差。比较大小用 `compareTo`，不要只用 `equals`（scale 不同可能 equals 为 false）。

随机：普通用 `ThreadLocalRandom`；安全相关用 `SecureRandom`。`UUID.randomUUID()` 常作幂等键或唯一标识。

### 5.3 时间日期

旧 `Date`/`Calendar` 可变、API 混乱，新代码用 `java.time`。  
- `Instant`：时间线上的一点（UTC 本质）。  
- `LocalDate`/`LocalTime`/`LocalDateTime`：无时区的人历日期时间。  
- `ZonedDateTime`：带时区。  
- `Duration`/`Period`：时间量。  
格式化用 `DateTimeFormatter`。存储与展示要分清「时刻」与「本地日历」。

### 5.4 Objects 与 Optional

`Objects.requireNonNull`、`Objects.equals` 可减少样板与 NPE。`Optional` 用于「可能没有返回值」的明确表达；适合作为返回类型，不适合到处当字段或参数滥用。避免 `optional.get()` 不经判断；多用 `orElse`/`orElseGet`/`ifPresent`/`map`。

### 5.5 正则

`Pattern.compile` 预编译后复用。注意灾难性回溯（恶意输入拖垮 CPU）。简单分割不一定非用正则。

### 5.6 复习自检

- [ ] 说出 BigDecimal 正确构造与比较方式
- [ ] 区分 Instant 与 LocalDateTime
- [ ] 列举 Optional 的反模式

#### 自检答案

**1. BigDecimal 正确构造与比较**

- **构造**：用字符串或整数，避免 `double` 字面量  
  - 推荐：`new BigDecimal("0.1")`、`BigDecimal.valueOf(10)`、`BigDecimal.valueOf(1, 1)`（表示 `0.1`）  
  - 避免：`new BigDecimal(0.1)`（`0.1` 的二进制浮点误差会带进 BigDecimal）  
  - `BigDecimal.valueOf(0.1)` 虽比 `new BigDecimal(0.1)` 稍好（走 `Double.toString`），金额场景仍优先字符串
- **比较大小**：用 `compareTo`，看返回值与 `0` 的关系（`<0` / `==0` / `>0`）  
  - 不要只用 `equals`：`equals` 要求 value **与 scale 都相同**，故 `1.0` 与 `1.00` 的 `equals` 为 false，但 `compareTo` 为 `0`
- **运算**：加减乘除用 `add`/`subtract`/`multiply`/`divide`；除法必须指定舍入模式（如 `RoundingMode.HALF_UP`），否则无法整除会抛异常

**2. Instant 与 LocalDateTime**

| | `Instant` | `LocalDateTime` |
| --- | --- | --- |
| 含义 | 时间线上的一个绝对时刻 | 本地日历上的日期+时间 |
| 时区 | 本质是 UTC（epoch 秒/纳秒） | **无时区**，不是「全球唯一时刻」 |
| 典型用途 | 日志、订单创建时间、跨时区对齐、持久化时刻 | 排班、开场时间、生日提醒等「墙上时钟」 |
| 互转 | 需借助 `ZoneId`：`instant.atZone(zone).toLocalDateTime()` | `localDateTime.atZone(zone).toInstant()` |

一句话：`Instant` 回答「宇宙时钟几点」；`LocalDateTime` 回答「某地日历上显示几点」，同一 `LocalDateTime` 在不同时区对应不同 `Instant`。

**3. Optional 的反模式**

- 当作字段、方法参数、集合元素到处传（增加装箱与 API 噪音；参数用 `@Nullable` / 重载更清晰）
- 返回 `Optional` 后又立刻 `get()` 不判断，等于把 NPE 换成 `NoSuchElementException`
- `orElse(expensive())`：无论有无值都会先算默认值；应改用 `orElseGet(() -> expensive())`
- `Optional.of(nullable)`：值为 null 时直接 NPE；可能为空用 `ofNullable`
- 用 `Optional` 替代普通判空后仍写冗长 `isPresent` + `get`，而不用 `map`/`flatMap`/`ifPresent`/`orElse`
- 在 `Optional` 里塞业务异常控制流，或把「空集合」再包一层 `Optional`（空集合本身已表达「没有」）
- 序列化/持久化实体字段使用 `Optional`（多数框架不友好）

正用：主要作为**返回类型**表达「可能没有」；链式 `map`/`flatMap`/`filter`/`orElse`/`orElseThrow`。

---

## 6. 异常与断言

### 6.1 体系结构

`Throwable` 分 `Error`（如 OOM、栈溢出，一般不捕获）与 `Exception`。`Exception` 中 `RuntimeException` 及子类为非受检；其它多为受检，必须捕获或声明 `throws`。

设计初衷：受检强迫处理可恢复情况。现代框架常把受检包装成非受检，避免接口被 `throws` 污染；但 IO 等仍常见受检。

### 6.2 语法与资源管理

`try-catch-finally`：finally 几乎总执行（除非 JVM 炸掉等极端情况）。多个 catch 由窄到宽。`try-with-resources` 自动关闭 `AutoCloseable`，推荐替代手动 finally 关流；若关闭时再抛异常，会以抑制异常形式挂在主异常上。

自定义异常应提供清晰信息与 cause，便于排查。业务异常与系统异常分层，便于统一处理与映射错误码。

### 6.3 实践原则

- 不要空 catch。  
- 不要用异常做正常分支控制（贵且难读）。  
- finally 里不要 return（会掩盖原异常或返回值）。  
- 参数校验优先非法参数异常或明确错误返回，而不是 assert（assert 默认可能关闭）。

### 6.4 复习自检

- [ ] 列举常见 RuntimeException 及触发条件
- [ ] 说明 try-with-resources 的好处
- [ ] 解释受检异常在 API 设计上的利弊

#### 自检答案

**1. 常见 RuntimeException 及触发条件**

| 异常 | 典型触发条件 |
| --- | --- |
| `NullPointerException` | 对 `null` 解引用：调方法、取字段、自动拆箱（如 `Integer x = null; int y = x;`） |
| `IllegalArgumentException` | 参数不合法（空串、负数、非法枚举值等），主动校验时常抛 |
| `IllegalStateException` | 对象状态不允许当前操作（未初始化就用、已关闭再写、状态机乱跳） |
| `IndexOutOfBoundsException` | 数组/列表下标越界（含 `ArrayIndexOutOfBoundsException`、`StringIndexOutOfBoundsException`） |
| `ClassCastException` | 强制类型转换失败（如把 `Integer` 强转成 `String`） |
| `ArithmeticException` | 整数除零等（`1 / 0`）；注意浮点除零是 `Infinity`/`NaN`，一般不抛 |
| `NumberFormatException` | `Integer.parseInt("abc")` 等字符串无法解析为数字 |
| `UnsupportedOperationException` | 调用不支持的操作（如对不可变列表 `add`、未实现的可选方法） |
| `ConcurrentModificationException` | 用增强 for / Iterator 遍历时结构性修改集合（非 fail-safe 实现） |
| `NoSuchElementException` | `Iterator.next()` / `Optional.get()` 在没有元素时仍取值 |

另记：`Error` 子类如 `OutOfMemoryError`、`StackOverflowError` 一般不捕获、不恢复。

**2. try-with-resources 的好处**

- **自动关闭**：`try (...)` 结束（正常或异常）时按声明的**逆序**调用 `close()`，减少漏关流、句柄泄漏
- **代码更短**：不必手写 `finally { if (in != null) in.close(); }`，也少嵌套
- **异常更干净**：主逻辑抛出的异常是主异常；`close()` 再抛会作为**抑制异常**（suppressed）挂在主异常上，用 `getSuppressed()` 可查，不会盖掉原始原因（旧式 finally 里再抛常会掩盖）
- **可多资源**：括号里声明多个 `AutoCloseable`，关闭顺序正确、职责清晰
- **适用面广**：实现 `AutoCloseable`/`Closeable` 的流、连接、锁辅助类等都能用

**3. 受检异常在 API 设计上的利弊**

**利**

- 编译期强制调用方处理或声明 `throws`，不易「静默忽略」可恢复失败（如 IO、网络、解析）
- 把「预期可能失败」写进方法签名，契约更明确
- 适合库边界上调用方确实有处理手段的场景（重试、换路径、提示用户）

**弊**

- 签名传染：底层一抛，中间层被迫层层 `throws` 或空洞 catch，接口膨胀、难演进
- 与 Lambda / Stream 不友好：函数式接口多不声明受检异常，被迫包装或吞掉
- 易被滥用：空 `catch`、一律转 `RuntimeException`，失去强制处理的初衷
- 现代风格倾向：业务/框架内部多用非受检（或统一业务异常）；仅在真正可恢复且调用方必须知情时保留受检（经典如 `IOException`）

一句话：受检适合「调用方应当处理的可预期失败」；过度使用会污染 API，现代项目常在边界包装成非受检再统一处理。

---

## 7. 泛型

### 7.1 目的与擦除

泛型在编译期提供类型检查，减少强制转换。运行时发生**类型擦除**：大部分泛型信息被抹成原类型（如 `Object` 或边界类型）。因此不能 `new T()`、不能创建泛型数组（有限制）、运行时 `List<String>` 与 `List<Integer>` 的类对象相同。

擦除是为了兼容旧代码；也导致反射拿泛型信息受限。

### 7.2 通配符与 PECS

- `List<? extends Number>`：可安全 get 当 Number，不能随意 add（除 null）。适合「生产者」。  
- `List<? super Integer>`：可 add Integer，get 只能当 Object。适合「消费者」。  
PECS：Producer Extends, Consumer Super。无界 `?` 表示「某种未知类型」。

### 7.3 其它注意

原始类型 `List`（无 `<>`）关闭泛型检查，应避免。泛型方法可自行推断类型参数。多重边界 `T extends A & B`：类在前，接口在后。

### 7.4 复习自检

- [ ] 解释为何 `List<String>` 不能赋值给 `List<Object>`
- [ ] 用 PECS 说明一个 API 参数该用 extends 还是 super
- [ ] 说明擦除对运行时 `instanceof` 的影响

#### 自检答案

**1. 为何 `List<String>` 不能赋值给 `List<Object>`**

若允许：

```java
List<String> strings = new ArrayList<>();
List<Object> objects = strings; // 假设允许
objects.add(Integer.valueOf(1)); // 合法：List<Object> 可加任意 Object
String s = strings.get(0);       // 运行时拿到 Integer → ClassCastException
```

泛型在这里是**不变的（invariant）**：`List<String>` 与 `List<Object>` 无继承关系，即使 `String` 是 `Object` 的子类。  
这样编译期就能挡住「通过父类型引用往子类型集合塞脏数据」。

对比：

- 数组是**协变**的：`String[]` 可赋给 `Object[]`，但 `objects[0] = 1` 会在运行时 `ArrayStoreException`
- 泛型选择在编译期禁止赋值，把坑提前堵住
- 需要「只读子类型列表」时用 `List<? extends Object>`（或 `List<?>`），不能往里随意 `add`

**2. 用 PECS 说明 API 参数该用 extends 还是 super**

**PECS**：Producer Extends, Consumer Super。

| 角色 | 通配符 | 含义 | 能做什么 |
| --- | --- | --- | --- |
| 生产者（从集合**读出**数据给你） | `? extends T` | 元素是 T 的某个子类型 | 安全按 `T` get；不能随意 add（除 null） |
| 消费者（把你的数据**写入**集合） | `? super T` | 元素是 T 的某个父类型 | 可 add `T`；get 只能当 Object |

示例：

```java
// src 是生产者：只从中读 Number → extends
// dest 是消费者：往里写 Integer → super
void copy(List<? extends Number> src, List<? super Integer> dest) {
    for (Number n : src) {
        if (n instanceof Integer) {
            dest.add((Integer) n);
        }
    }
}
```

选型口诀：

- 参数主要用于 **get / 遍历产出** → `List<? extends Foo>`
- 参数主要用于 **add / 放入** → `List<? super Foo>`
- 既要准确 get 某具体类型又要 add 同类型 → 用确切类型 `List<Foo>`，不用通配符

**3. 擦除对运行时 `instanceof` 的影响**

编译后泛型参数被擦除，运行时大致只剩原类型（如 `List`）或边界类型（如 `Number`），**没有** `List<String>` 与 `List<Integer>` 的区分。

因此：

- **非法**：`obj instanceof List<String>`（编译错误）——运行时无法检查类型实参
- **合法**：`obj instanceof List<?>` 或 `obj instanceof List`（只判断是不是 List）
- `List<String>` 与 `List<Integer>` 的 `getClass()` 相同，都是同一 Class 对象
- 不能 `new T()`、不能可靠创建 `T[]`；反射拿到的往往是擦除后的信息（除非有 `ParameterizedType` 等残留，如字段声明上的泛型签名）

实践：运行时类型判断只针对擦除后的裸类型；元素真实类型要靠自己约定、强制转换或额外存 `Class<T>`。

---

## 8. 集合框架

### 8.1 总览

`Collection` 存单一元素序列/集合；`Map` 存键值对。常见实现需掌握时间复杂度直觉与是否允许 null、是否有序、是否线程安全。

迭代器提供统一遍历；fail-fast 集合在迭代期间结构性修改可能立刻抛 `ConcurrentModificationException`（尽力检测，非绝对）。并发集合常为弱一致迭代。

### 8.2 List

**ArrayList**  
动态数组。随机访问 O(1)；中间插入删除要搬移元素。扩容通常 1.5 倍，有复制成本。默认非线程安全。

**LinkedList**  
双向链表。头尾操作快；按下标访问慢。实际中若主要是随机访问，ArrayList 往往更快。

**CopyOnWriteArrayList**  
写时复制，读多写少、迭代要求快照语义时有用；写很贵。

### 8.3 Set

**HashSet**  
靠 HashMap 实现，唯一性依赖 equals/hashCode。无序。

**LinkedHashSet**  
保持插入顺序。

**TreeSet**  
红黑树，按 Comparable/Comparator 排序。元素必须可比较且比较一致。

### 8.4 Queue 与 Deque

**ArrayDeque**  
两端队列，一般优于 Stack/LinkedList 作栈/队列。

**PriorityQueue**  
堆结构，队首是最小/最大优先级；遍历顺序不是完全排序。

阻塞队列见并发章。

### 8.5 Map

**HashMap**  
数组 + 链表/红黑树（冲突严重时树化）。平均 O(1)。允许一个 null 键。负载因子默认 0.75，超过阈值扩容。键的 hashCode 必须稳定；可变键改字段后可能「找不到」。

**LinkedHashMap**  
可按插入序或访问序；访问序可辅助做 LRU。

**TreeMap**  
有序 Map。

**ConcurrentHashMap**  
并发场景常用；不允许 null 键/值。细节见并发章。

### 8.6 工具类

`Collections.sort`、不可变包装、同步包装（粗粒度锁，现代更倾向并发集合）。`Comparator.comparing(...).thenComparing(...)` 链式比较器。

### 8.7 复习自检

- [ ] 对比 ArrayList 与 LinkedList 的适用场景
- [ ] 说明 HashMap 对 equals/hashCode 的依赖
- [ ] 说出遍历中安全删除元素的方式

---

## 9. IO 与 NIO

### 9.1 传统 IO

字节流（`InputStream`/`OutputStream`）处理原始字节；字符流（`Reader`/`Writer`）处理文本并涉及编码。常用装饰器：缓冲流提高性能，转换流桥接字节与字符。

必须关闭资源：优先 try-with-resources。大文件避免一次性读入内存。

**序列化**  
`Serializable` 可把对象变成字节流。版本、安全（反序列化漏洞）、性能都是问题；业务上更常见 JSON/Protobuf 等，而不是 Java 原生序列化。

### 9.2 NIO 概念

`Channel` 双向通道；`Buffer` 数据容器；`Selector` 多路复用（网络服务端）。`ByteBuffer` 的 `flip`/`clear`/`compact` 需分清读写模式切换。入门阶段理解「非阻塞与缓冲」即可；系统准备学 Netty 前见 [Netty 前置基础](./Netty前置基础.md)。

### 9.3 NIO.2 文件 API

`Path`/`Files` 比 `java.io.File` 更现代：拷贝、读写、目录遍历、属性、符号链接等更清晰。日常文件操作优先这套。

### 9.4 复习自检

- [x] 说明字符流与字节流的分工
- [x] 解释为何必须指定 Charset
- [x] 写出 try-with-resources 关闭两个资源的形态

**参考答案**

**1. 字符流与字节流的分工**

| | 字节流 | 字符流 |
| --- | --- | --- |
| 基类 | `InputStream` / `OutputStream` | `Reader` / `Writer` |
| 处理单位 | 原始字节（`byte`） | 字符（`char`，UTF-16 码元） |
| 典型场景 | 图片、音频、压缩包、任意二进制 | 文本文件、日志、配置（涉及编码） |
| 与编码 | 不解释「字节如何组成文字」 | 读写文本时在字节 ↔ 字符间按 Charset 转换 |

桥接：`InputStreamReader` / `OutputStreamWriter` 把字节流接到字符流；缓冲常用 `BufferedInputStream` / `BufferedReader` 等。

一句话：二进制用字节流；文本用字符流（底层仍是字节，字符流负责编解码）。

**2. 为何必须指定 Charset**

磁盘/网络上文本本质是字节序列；同一段中文用 UTF-8、GBK 编出来的字节不同。读时 Charset 不对会乱码，写时未约定则依赖「平台默认编码」，换机器/换 OS 结果可能不一致。

因此：读写文本应显式指定（如 `StandardCharsets.UTF_8`），读写双方约定同一编码，避免依赖 `Charset.defaultCharset()`。

**3. try-with-resources 关闭两个资源**

```java
try (InputStream in = Files.newInputStream(path);
     OutputStream out = Files.newOutputStream(dest)) {
    in.transferTo(out);
} // 结束时按声明的逆序自动 close；异常会被抑制挂到主异常上
```

多个资源用分号分隔写在 `try (...)` 里即可；无需手写 `finally` 关流。

---

## 10. 反射、注解与枚举

### 10.1 反射

运行时获取类信息并调用构造/方法/字段。入口是 `Class` 对象。可绕过访问控制（`setAccessible`，模块系统下受限更多）。代价：性能较差、代码脆弱、安全风险。框架（注入、序列化、ORM）大量使用；业务代码应少直接用。

动态代理：JDK 代理基于接口；CGLIB 等可代理类。AOP 常见实现基础。

### 10.2 注解

注解是元数据。`@Retention` 决定源码/字节码/运行时是否保留；`@Target` 限制可标记位置。自定义注解 + 反射或编译期处理可实现配置与校验。框架注解（如依赖注入相关）本质也是约定 + 处理。

### 10.3 枚举

`enum` 是类型安全的常量集合，可有字段、方法、实现接口。比较可用 `==`。适合状态机、选项集合。`EnumSet`/`EnumMap` 对枚举键更高效。

### 10.4 复习自检

- [x] 说出反射的三大用途与两大代价
- [x] 解释 RUNTIME 与 SOURCE 保留策略
- [x] 说明枚举单例的优点

**参考答案**

**1. 反射的三大用途与两大代价**

三大用途（常见归纳）：

1. **运行时探查类型信息**：拿到 `Class`，查字段、方法、构造、注解、父类/接口等
2. **动态创建与调用**：`newInstance` / `Constructor.newInstance`、`Method.invoke`、读写字段；框架做依赖注入、ORM、序列化、配置驱动加载
3. **动态代理 / AOP 基础**：按接口生成代理（JDK）或增强类（CGLIB 等），在调用前后织入逻辑

两大代价（大纲强调的核心代价；亦可答「性能 + 安全/脆弱」）：

1. **性能较差**：反射调用绕过常规调用路径，无法充分内联优化，通常慢于直接调用
2. **代码脆弱与安全风险**：字符串方法名/字段名无编译期检查，重构易静默坏掉；`setAccessible` 破坏封装，模块系统下更受限，也带来安全隐患

**2. RUNTIME 与 SOURCE 保留策略**

`@Retention` 决定注解信息保留到哪一层：

| 策略 | 保留位置 | 典型用途 |
| --- | --- | --- |
| `SOURCE` | 仅源码，编译后丢弃 | 给编译器/工具看，如 `@Override`、`@SuppressWarnings` |
| `CLASS`（默认） | 进字节码，运行时默认不可反射读取 | 偏字节码工具/部分框架处理 |
| `RUNTIME` | 源码 + 字节码 + 运行时可通过反射读取 | Spring、校验、自定义运行时配置，如 `@Autowired` 一类 |

对比要点：`SOURCE` 运行时「看不见」；`RUNTIME` 才能 `getAnnotation` / 框架在启动期扫描使用。

**3. 枚举单例的优点**

写法：`enum Singleton { INSTANCE; }`

优点：

1. **写法简单**：JVM 保证枚举常量只初始化一次
2. **天生线程安全**：类加载机制保证，无需自己加锁或 DCL
3. **防反射破坏**：不能像普通类那样随意 `Constructor.newInstance` 再造一个（会失败）
4. **防反序列化再造实例**：枚举反序列化有特殊机制，仍回到已有常量，不易出现「双实例」
5. **可用 `==` 比较**：身份唯一、语义清晰

Effective Java 也因此推荐枚举实现单例。

---


## 11. 并发编程基础

### 11.1 线程基础

线程是调度的基本单位。创建：`Thread`、`Runnable`、`Callable`+线程池。`start` 才启动新线程；直接 `run` 只是普通方法调用。

状态包括新建、可运行、阻塞、等待、限时等待、终止。理解「阻塞在锁」与「wait 等待」不同。中断是协作式的：设中断标志，被调用方需响应；不是强制杀死线程。

### 11.2 synchronized 与 wait/notify

`synchronized` 保证同一锁上互斥，并提供可见性与有序性语义。锁对象可以是 `this`、类对象或私有 final 对象（推荐避免对外暴露锁）。

`wait`/`notify`/`notifyAll` 必须在持有锁时调用。等待条件要用 while 检查，防止虚假唤醒。典型模式：生产者消费者。

### 11.3 volatile 与原子类

`volatile` 保证可见性与禁止部分重排序，**不保证** `i++` 这类复合操作的原子性。适合旗标、发布不可变状态等。

`AtomicInteger` 等用 CAS 做无锁原子更新。高竞争下 `LongAdder` 等更适合统计。

### 11.4 显式锁与同步工具

`ReentrantLock`：可中断、可超时、可公平、可多条件队列，比 synchronized 更灵活，也更易用错（必须 finally unlock）。

`CountDownLatch`：一次倒数等待。  
`CyclicBarrier`：多人汇合可循环。  
`Semaphore`：限流许可。  

`ThreadLocal`：每线程一份变量副本；用完 `remove`，避免线程池场景内存泄漏。

### 11.5 线程池

核心是 `ThreadPoolExecutor`：核心线程数、最大线程数、空闲存活、工作队列、拒绝策略、线程工厂。任务先被核心线程接，忙则入队，队列满再扩到最大，再满则拒绝。

固定/缓存线程池等便捷工厂方法可能隐藏无界队列风险，导致 OOM；生产环境宜显式构造并命名线程、设边界队列与拒绝策略。

`CompletableFuture` 适合组合异步结果，注意异常与线程池传递。

### 11.6 并发集合与问题

`ConcurrentHashMap` 高并发读写下比加一把大锁的 HashMap 更合适。`CopyOnWriteArrayList`、各类 `BlockingQueue` 支撑并发模型。

死锁：互斥、占有且等待、不可抢占、循环等待。预防：固定加锁顺序、超时锁、减少锁范围。`jstack` 可辅助分析。

### 11.7 复习自检

- [x] 对比 synchronized、volatile、Atomic
- [x] 默写线程池关键参数含义
- [x] 说明 ThreadLocal 泄漏场景

**参考答案**

**1. 对比 synchronized、volatile、Atomic**

| | `synchronized` | `volatile` | `Atomic*`（如 `AtomicInteger`） |
| --- | --- | --- | --- |
| 互斥 / 原子性 | 临界区内复合操作可原子完成 | 单次读/写有序可见；**不保证** `i++` 原子 | 提供的 CAS 方法（如 `incrementAndGet`）原子 |
| 可见性 | 有（解锁对后续加锁） | 有（写对后续读） | 有 |
| 有序性 | 临界区边界约束重排 | 禁止该变量相关重排 | 依赖原子操作语义 |
| 阻塞 | 抢不到锁会阻塞 | 不阻塞 | 一般自旋重试，不进管程等待 |
| 典型用途 | 保护一段共享逻辑、多字段一致更新 | 旗标、开关、安全发布引用 | 单变量计数、无锁状态机 |

选型口诀：

- 要保护「读改写多步 / 多字段」→ `synchronized` 或 `ReentrantLock`
- 只要「一个开关被别的线程看见」→ `volatile` 往往够
- 只要「一个计数器原子加减」→ `AtomicInteger` / 高竞争统计用 `LongAdder`

**2. 线程池关键参数含义**

以 `ThreadPoolExecutor` 为准：

| 参数 | 含义 |
| --- | --- |
| `corePoolSize` | 核心线程数；即使空闲通常也保留（除非允许超时回收核心线程） |
| `maximumPoolSize` | 最大线程数；队列满后才可能扩到此值 |
| `keepAliveTime` + 时间单位 | 超过 core 的空闲线程存活多久后回收 |
| `workQueue` | 任务队列；生产应用**有界**队列（如 `ArrayBlockingQueue`） |
| `threadFactory` | 如何创建线程（命名、是否守护等） |
| `handler` | 拒绝策略：队列满且线程达 max 时怎么处理 |

提交任务时大致顺序：

1. 当前线程数小于 core → 新建核心线程执行  
2. 否则尝试入队  
3. 队列满且线程数小于 max → 再建线程  
4. 仍无法接收 → 执行拒绝策略（默认抛异常；也有调用者运行、丢弃等）

补充：`Executors.newFixedThreadPool` 等工厂常用无界队列，任务堆积可能 OOM；生产宜显式构造上述参数。

**3. ThreadLocal 泄漏场景**

`ThreadLocal` 把值存在**当前线程**的 `ThreadLocalMap` 里。键是 `ThreadLocal` 的弱引用，值是强引用。

典型泄漏（尤其**线程池**）：

1. 请求线程从池中借出，执行业务时 `threadLocal.set(ctx)`  
2. 请求结束**没有** `remove()`  
3. 线程归还线程池继续存活 → `ThreadLocalMap` 条目长期留着  
4. 结果：value（如大型对象、用户上下文）无法被 GC；或下一个请求误读到上一个请求的残留数据（串数据）

因此规范写法：

```java
try {
    USER.set(user);
    // 业务
} finally {
    USER.remove();
}
```

一句话：线程池线程长生命周期 + 只 `set` 不 `remove` = 内存占用与上下文串扰风险。

---

## 12. JVM 与内存模型入门

### 12.1 运行时区域（概念模型）

- 栈：每线程私有，存栈帧（局部变量、操作数栈等）。  
- 堆：对象实例（几乎），线程共享，GC 主战场。  
- 方法区/元空间：类元数据等。  
- 程序计数器：当前执行位置。  

出栈溢出多为深递归或大量局部分配；堆溢出多为对象过多或泄漏。

### 12.2 GC 入门

对象可达性分析：从 GC Roots 出发不可达则可回收。新生代朝生夕死用复制类算法更常见；老年代更稳重。G1/ZGC 等面向低延迟大堆。初学掌握「为何需要 GC、什么是 Stop-The-World、如何看 GC 日志」即可。

引用级别：强引用普通对象；软引用缓存；弱引用如 WeakHashMap；虚引用更少见。

### 12.3 JMM 与 happens-before

Java 内存模型规定线程如何通过主内存交互，以及哪些操作有序可见。`synchronized`/`volatile`/`final` 与线程启动结束等建立 happens-before。不必死记全部规则，但要理解「没同步的共享可变状态会出诡异 bug」。

### 12.4 类加载与双亲委派

类加载大致：加载 → 链接 → 初始化。双亲委派：先让父加载器尝试，避免核心类被篡改、保证唯一性。SPI、热部署等会打破委派，属于进阶。

### 12.5 复习自检

- [ ] 区分栈溢出与堆溢出的典型原因
- [ ] 用自己的话解释可达性分析
- [ ] 说明双亲委派的目的

---

## 13. Java 8+ 现代特性

### 13.1 Lambda 与方法引用

Lambda 表达「一段可传递的行为」，目标类型必须是函数式接口。捕获外部局部变量需实质 final。避免在 Lambda 里修改共享可变状态。方法引用是更短的写法（`String::isBlank`）。

### 13.2 Stream

Stream 是数据处理管道：创建 → 中间操作（惰性）→ 终端操作（触发计算）。常用 `filter`/`map`/`flatMap`/`collect`。`collect(Collectors.toList())` 等。

并行流用公共 ForkJoinPool，适合纯计算、无共享变异、数据足够大；小集合或涉及同步/IO 可能更慢或更错。

### 13.3 语言演进要点

- `var`：局部变量类型推断，勿滥用到失去可读性。  
- 文本块：多行字符串。  
- `record`：透明不可变数据载体，自动 equals/hashCode/toString。  
- `switch` 表达式、模式匹配：减少样板。  
- 密封类：限制谁可以继承/实现。  
- 虚拟线程（21）：高并发阻塞风格 IO 的新选项，概念上了解「轻量线程由 JVM 调度」。

### 13.4 复习自检

- [x] 手写 Stream 分组统计骨架
- [x] 说明并行流风险
- [x] 对比 record 与手写不可变类

**参考答案**

**1. Stream 分组统计骨架**

典型：按某字段分组，再计数 / 求和。

```java
record Order(String city, int amount) {}

List<Order> orders = List.of(
        new Order("BJ", 10),
        new Order("SH", 20),
        new Order("BJ", 15)
);

// 按城市分组
Map<String, List<Order>> byCity = orders.stream()
        .collect(Collectors.groupingBy(Order::city));

// 按城市计数
Map<String, Long> countByCity = orders.stream()
        .collect(Collectors.groupingBy(Order::city, Collectors.counting()));

// 按城市求和
Map<String, Integer> sumByCity = orders.stream()
        .collect(Collectors.groupingBy(
                Order::city,
                Collectors.summingInt(Order::amount)
        ));

// 先过滤再分组（常用骨架）
Map<String, Long> skeleton = orders.stream()
        .filter(o -> o.amount() > 0)
        .collect(Collectors.groupingBy(Order::city, Collectors.counting()));
```

口诀：`stream` →（可选 `filter`/`map`）→ `collect(groupingBy(分类器 [, 下游 Collectors]))`。

**2. 并行流风险**

`stream().parallel()` / `parallelStream()` 默认用公共 `ForkJoinPool.commonPool()`。

主要风险：

1. **线程安全**：中间若修改共享可变状态（外部 `List.add`、非并发 Map），会丢数据或抛异常；终端收集要用线程安全方式或纯函数式归约  
2. **公共池争用**：和其它并行流、部分 ForkJoin 任务抢同一池，可能互相拖慢；重活宜自建池（进阶）  
3. **不适合 IO / 锁**：阻塞 IO、`synchronized` 重的代码在并行流里容易把公共池线程占满  
4. **小数据更慢**：拆分、合并、调度有开销，列表很小时串行往往更快  
5. **顺序语义**：`findFirst`、依赖顺序的操作在并行下行为/性能与串行不同，需分清 `findAny` 等  
6. **调试与异常**：堆栈、断点更难跟；某分片异常会影响整次计算  

适用边界：纯计算、无共享变异、数据量足够大、可拆分的任务。否则优先普通串行流。

**3. record 与手写不可变类**

| | `record` | 手写不可变类 |
| --- | --- | --- |
| 样板代码 | 自动生成构造、访问器、`equals`/`hashCode`/`toString` | 需自己写字段、构造、getter、三件套 |
| 意图 | 透明数据载体（浅不可变：组件引用可变对象时仍可能被改内容） | 可精细控制封装、校验、拷贝 |
| 继承 | 隐式 `final`，不能再继承别的类（可实现接口） | 可设计继承体系（若需要） |
| 可变性 | 组件字段隐式 `final`，无 setter | 靠 `final` 字段 + 不提供 setter + 防御性拷贝 |
| 适用 | DTO、键值对、统计行、简单值对象 | 要隐藏表示、惰性计算、复杂不变式、兼容旧 API |

手写骨架对照：

```java
// record
public record UserId(long id) {}

// 手写不可变
public final class UserIdManual {
    private final long id;
    public UserIdManual(long id) { this.id = id; }
    public long id() { return id; }
    @Override public boolean equals(Object o) { /* ... */ }
    @Override public int hashCode() { return Long.hashCode(id); }
    @Override public String toString() { return "UserIdManual[id=" + id + "]"; }
}
```

选型：只要「一组不可变数据 + 正确相等性」，优先 `record`；要强封装或复杂行为再用手写不可变类。

---

## 14. 模块化、工具链与工程实践

### 14.1 构建工具

Maven/Gradle 管理依赖与构建生命周期。理解依赖范围：`compile`/`runtime`/`provided`/`test`。会看依赖树、排除冲突。版本对齐避免「本地能跑、CI 挂」。

### 14.2 代码质量

统一命名与格式；关键路径打日志用占位符；异常带上下文；魔法值抽常量或枚举。单元测试覆盖核心分支；测试替身（mock）隔离外部依赖。

### 14.3 设计原则（基础）

单一职责、开闭、里氏替换、接口隔离、依赖倒置——各能举一例即可。优先简单方案；过早抽象也是成本。

### 14.4 安全常识

SQL 用参数绑定防注入；不要把密钥写进仓库；密码哈希加盐；日志脱敏；谨慎反序列化不可信数据。

### 14.5 复习自检

- [x] 解释 test 范围依赖为何不会进生产包
- [x] 说出三条日志与异常规范
- [x] 说明构造器注入的好处（相对字段注入）

**参考答案**

**1. test 范围依赖为何不会进生产包**

Maven（Gradle 类似）用**依赖范围（scope）**控制「哪一阶段的 classpath 能看见该依赖」。

| 范围 | compile | test | 打包进生产 jar/war？ |
| --- | --- | --- | --- |
| `compile`（默认） | ✓ | ✓ | 通常会（或作为传递依赖参与运行） |
| `test` | ✗ | ✓ | **不会** |

`test` 范围（如 JUnit、Mockito）只加入：

- 测试编译 classpath  
- 测试运行 classpath  

**不参与**主代码编译，也**不会**打进最终生产制品（`package` 出的业务包 / 运行时依赖集合）。  
因此生产环境跑主程序时根本加载不到这些库——既减小包体，也避免测试工具类进入线上。

一句话：构建工具按 scope 隔离测试依赖；打包生产产物时故意排除 `test`。

**2. 三条日志与异常规范**

可记下面三条（与 14.2 一致，够用即可）：

1. **日志用占位符，勿字符串拼接**  
   `log.info("orderId={} status={}", id, status);`  
   避免无谓拼接、也利于级别关闭时少做无用功。

2. **异常要带上下文，不要空 catch / 只打 stack 不说明业务**  
   捕获后要么处理，要么包装后抛出并附上关键参数（订单号、用户等）；禁止 `catch (Exception e) {}`。

3. **按级别说话：错误用 error/warn，排查用 info/debug；敏感信息脱敏**  
   密码、token、身份证等不上日志；生产默认级别勿过滥刷 debug。

（可补充：异常用于异常路径，不用来做正常分支控制。）

**3. 构造器注入相对字段注入的好处**

```java
// 构造器注入（推荐）
@Service
public class OrderService {
    private final OrderRepo repo;
    public OrderService(OrderRepo repo) {
        this.repo = repo;
    }
}

// 字段注入（不推荐）
@Autowired
private OrderRepo repo;
```

主要好处：

1. **依赖必填、不可变**：可用 `final`，创建后不能改，对象始终完整  
2. **便于单测**：`new OrderService(mockRepo)` 即可，不必起容器或反射塞字段  
3. **依赖关系一目了然**：看构造参数就知道需要什么，隐藏的 `@Autowired` 字段容易漏看  
4. **避免循环依赖被暂时「遮住」**：构造器注入时循环依赖往往启动失败，逼你改设计；字段注入可能推迟到运行期才暴露  
5. **与容器解耦更好**：不依赖 Spring 注解也能纯 Java 组装（字段注入常绑在容器反射上）

一句话：构造器注入让依赖显式、可测、可 `final`；字段注入省事但不利于测试与设计。

---

## 15. 总自检清单

### 15.1 必须能口述

- [x] 值传递；`==` 与 `equals`；包装类缓存
- [x] 重写与重载；多态规则（方法 vs 字段/静态）
- [x] equals/hashCode 契约
- [x] ArrayList vs LinkedList；HashMap 结构与扩容直觉
- [x] 受检 vs 非受检；try-with-resources
- [x] 泛型擦除与 PECS
- [x] synchronized / volatile / CAS
- [x] 线程池核心参数与拒绝策略
- [x] 堆与栈；GC 基本概念；双亲委派目的
- [x] Stream 惰性；并行流适用边界

**参考答案（口述版）**

**1. 值传递；`==` 与 `equals`；包装类缓存**

- Java **只有值传递**：传的是实参的一份拷贝。基本类型拷贝的是数值；引用类型拷贝的是**引用值**（可理解为地址的副本），不是对象本身。
- `==`：基本类型比数值；引用类型比**是否同一对象**（同一引用）。
- `equals`：可定义**逻辑相等**（内容是否相同）；未重写时 `Object.equals` 等价于 `==`。
- 包装类缓存：`Integer` 等在常用范围（如 -128～127）可能复用同一实例，该范围内 `==` 可能为 true，范围外常为 false；比较内容应用 `equals`。

**2. 重写与重载；多态规则**

- **重载**：同一类中方法名相同、参数列表不同；编译期按引用类型静态选定。
- **重写**：子类覆盖父类实例方法；运行期按**实际对象类型**绑定（动态分派）。
- **字段 / static 方法**：不走运行期多态，看**编译期类型**（隐藏/遮蔽，不是重写）。

**3. equals/hashCode 契约**

- `equals` 相等 ⇒ `hashCode` 必须相等；参与比较的字段应一致。
- 重写 `equals` 必须重写 `hashCode`（否则 HashMap/HashSet 错乱）。
- 作为 Map 键时，相等相关字段放入后不应再变。

**4. ArrayList vs LinkedList；HashMap 结构与扩容**

- **ArrayList**：动态数组，随机访问 O(1)，中间增删要搬移；扩容约 **1.5 倍**。
- **LinkedList**：双向链表，头尾快，按下标慢；日常随机访问优先 ArrayList。
- **HashMap**：数组 + 链表/红黑树；负载因子默认 0.75，超阈值扩容为原容量 **2 倍**（容量为 2 的幂）；键依赖稳定的 `hashCode`/`equals`。

**5. 受检 vs 非受检；try-with-resources**

- **受检异常**（非 RuntimeException 的 Exception）：必须捕获或声明 `throws`。
- **非受检**：RuntimeException / Error，可不声明。
- **try-with-resources**：`try (Resource r = ...) { }`，结束时自动 `close`；多资源写在同一 `try` 里用 `;` 分隔。

**6. 泛型擦除与 PECS**

- 编译后类型参数被擦除，运行时基本只剩原类型/边界；故无 `new T()`、`instanceof List<String>` 等。
- **PECS**：生产者用 `? extends T`（适合读）；消费者用 `? super T`（适合写）；既读又写用确切类型。

**7. synchronized / volatile / CAS**

- **synchronized**：互斥 + 可见；保护复合临界区。
- **volatile**：可见性与相关有序性；**不保证** `i++` 原子。
- **CAS**（原子类）：无锁比较并交换，适合单变量原子更新。

**8. 线程池核心参数与拒绝策略**

- 参数：`corePoolSize`、`maximumPoolSize`、`keepAliveTime`、`workQueue`、`threadFactory`、`handler`。
- 流程：小于 core 建线程 → 否则入队 → 队列满且小于 max 再建 → 仍满则拒绝。
- 拒绝策略：Abort（抛异常）、CallerRuns、Discard、DiscardOldest 等；生产用有界队列。

**9. 堆与栈；GC；双亲委派**

- **栈**：线程私有，栈帧（局部变量等）；深递归易栈溢出。
- **堆**：线程共享，对象实例；GC 主战场；对象过多/泄漏易堆 OOM。
- **GC**：可达性分析，从 GC Roots 不可达则可回收；分代、STW 等。
- **双亲委派**：先让父加载器加载，保护核心类、保证同类唯一。

**10. Stream 惰性；并行流边界**

- 中间操作惰性，终端操作才触发计算。
- 并行流用公共 ForkJoinPool；适合大数据纯计算、无共享变异；小数据、IO、改共享状态时可能更慢或更错。

---

### 15.2 必须能手写

- [x] 一种线程安全单例
- [x] 生产者-消费者（BlockingQueue 或 wait/notify）
- [x] 自定义作为 HashMap 键的类（正确 equals/hashCode）
- [x] Stream 过滤 + 映射 + 分组
- [x] 简单不可变类或 record

**参考答案（手写骨架）**

**1. 线程安全单例（枚举或 DCL 任选其一）**

```java
// 推荐：枚举
public enum Singleton {
    INSTANCE;
}

// 或：双重检查
public class SingletonDcl {
    private static volatile SingletonDcl instance;
    private SingletonDcl() {}
    public static SingletonDcl getInstance() {
        if (instance == null) {
            synchronized (SingletonDcl.class) {
                if (instance == null) {
                    instance = new SingletonDcl();
                }
            }
        }
        return instance;
    }
}
```

**2. 生产者-消费者**

```java
// 方式 A：BlockingQueue
BlockingQueue<Integer> q = new ArrayBlockingQueue<>(100);
// 生产者：q.put(x);  消费者：q.take();

// 方式 B：wait/notify 有界缓冲（持锁 + while 判条件）
synchronized (lock) {
    while (满) lock.wait();
    // 放入
    lock.notifyAll();
}
synchronized (lock) {
    while (空) lock.wait();
    // 取出
    lock.notifyAll();
}
```

**3. HashMap 键类**

```java
public final class UserId {
    private final long id;
    public UserId(long id) { this.id = id; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId other)) return false;
        return id == other.id;
    }
    @Override
    public int hashCode() { return Long.hashCode(id); }
}
```

**4. Stream 过滤 + 映射 + 分组**

```java
Map<String, Long> map = list.stream()
        .filter(o -> o.amount() > 0)
        .map(o -> o.city())
        .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
// 或一步：
// .collect(Collectors.groupingBy(Order::city, Collectors.counting()));
```

**5. record / 不可变类**

```java
public record User(long id, String name) {}

// 或手写：final 字段 + 无 setter + equals/hashCode
public final class UserManual {
    private final long id;
    private final String name;
    public UserManual(long id, String name) {
        this.id = id;
        this.name = Objects.requireNonNull(name);
    }
    public long id() { return id; }
    public String name() { return name; }
    // equals / hashCode / toString ...
}
```

---

### 15.3 建议学习节奏（示例）

| 阶段 | 内容 |
| --- | --- |
| 第 1～2 天 | 类型、方法、数组、字符串 |
| 第 3～5 天 | OOP 全章 + Object 契约 |
| 第 6～8 天 | 集合 + 泛型 |
| 第 9～10 天 | 异常、常用 API、时间、IO |
| 第 11～14 天 | 并发 + JMM/JVM 入门 |
| 第 15～16 天 | Java 8+ 与工程实践 |
| 第 17 天 | 总清单盲测与补漏 |

---

## 附录 A：易混淆对照

| A | B | 区别 |
| --- | --- | --- |
| 重载 | 重写 | 编译期选方法 vs 运行期绑定 |
| `==` | `equals` | 默认比引用/值 vs 可定义语义相等 |
| String | StringBuilder | 不可变 vs 可变缓冲 |
| Checked | Unchecked | 是否强制声明/捕获 |
| `? extends` | `? super` | 适合读 vs 适合写 |
| sleep | wait | 不释放监视器锁 vs 释放并等待 |
| 堆 | 栈 | 共享对象内存 vs 线程私有帧 |

---

## 附录 B：推荐资源

- 《Java 核心技术 卷 I》：语法、OOP、集合  
- 《Java 并发编程实战》：并发体系  
- 《深入理解 Java 虚拟机》：JVM 选读  
- OpenJDK / Oracle 对应版本官方文档  

---

## 附录 C：笔记模板

每个三级标题建议记四栏：

1. **定义**（自己的话）  
2. **例子**（短代码）  
3. **坑**（至少一条）  
4. **关联点**（与其它章的链接，如 HashMap↔equals）

学完一章合上材料口述 3 分钟；卡壳处列入次日优先。

---

**说明**：本文是「带讲解的复习大纲」，不是替代专题书的完整教材。细节以你所用 JDK 版本文档为准；并发与 JVM 可在基础过关后继续加深。
