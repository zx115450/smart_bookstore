# Java 反射原理（框架间接使用）

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 工程基础（弱耦合，建议结合项目环境练习）  
> **关联包 / 模块**：`com.zx.config（间接）`  
> **建议前置**：Java 基础；可对照 [Java 基础复习大纲](./learning/Java基础复习大纲.md)

## 学习目标

1. 理解 Class / Method / Field 反射 API 的基本用法
2. 认识 Spring / MyBatis 运行时如何借助反射装配
3. 区分「手写反射」与「框架代劳反射」

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/zx/config/MybatisPlusConfig.java` | 对照阅读 |
| `pom.xml` | 对照阅读 |

读理论时请打开上表文件对照；改代码前先跑通 `docker compose up -d` 与 `local` profile。

---

## 导读
写 Spring Boot 时，你一定写过这种代码：

```java
@RestController
@RequestMapping("/api/books")
public class BookController {
    @Autowired private BookCatalogService service;
    @GetMapping("/{id}") public BookResponse get(@PathVariable Long id) { ... }
}
```

你有没有想过：**Tomcat 收到一个 HTTP 请求后，凭什么能找到 `BookController`、调用 `get` 方法、还把路径变量塞进 `id` 参数？** 它在编译期根本不知道你会写哪些 Controller。

答案就两个字：**反射**。

本文从「反射是什么」讲起，把 `Class` / `Field` / `Method` / `Constructor` 四大核心 API、动态代理、注解解析串起来，最后回到智慧书城 `smart_bookstore` 看反射在 Spring AI Tool Calling 里的真实落地。

---

## 一、反射到底是什么

### 1.1 一个直觉的定义

**普通调用**：编译期就知道调谁。
**反射调用**：运行期才决定调谁——靠一个「类名字符串」或「Class 对象」去操作它的字段、方法、构造器。

```java
// 普通调用：编译期就锁定
Book book = new Book();
book.setTitle("Redis");

// 反射调用：运行期才决定调哪个类的哪个方法
Class<?> clazz = Class.forName("com.zx.bookstore.catalog.entity.Book");
Object obj = clazz.getDeclaredConstructor().newInstance();
Method setter = clazz.getMethod("setTitle", String.class);
setter.invoke(obj, "Redis");
```

两段代码做的事一模一样，区别在于**第二段把「类 / 方法 / 参数类型」全部当成运行期数据来处理**。

### 1.2 为什么需要它

反射解决的核心矛盾是：**框架要在「不知道用户会写什么类」的前提下，调用用户的类**。

| 场景 | 谁用反射 | 干什么 |
|------|----------|--------|
| Spring IoC | 容器 | 扫描 `@Component`，`new` 出 Bean 塞进容器 |
| Spring MVC | DispatcherServlet | 按 URL 找 `@RequestMapping` 方法，反射调用 |
| MyBatis | SqlSession | 把 ResultSet 列反射赋给实体字段 |
| Jackson | ObjectMapper | 把 JSON 字段反射写入 POJO |
| JUnit | Runner | 找 `@Test` 方法反射执行 |
| Spring AI Tool | ChatClient | 把 `@Tool` 方法注册成大模型可调用的函数 |

没有反射，这些框架都得让用户写一堆「注册代码」，框架的「开箱即用」就无从谈起。

### 1.3 反射的代价

反射不是免费的：

- **性能**：反射调用要走方法查找、参数装箱、访问检查，比直接调用慢几倍到几十倍（JIT 优化后差距缩小，但仍存在）。
- **安全**：可以绕过访问修饰符（`setAccessible(true)`），破坏封装。
- **可读性**：IDE 跳转、编译期类型检查全部失效，重构时容易漏改。

所以原则是：**框架用反射，业务代码尽量少用**。真要在业务里用，先想想能不能用接口/泛型/枚举替代。

---

## 二、四大核心 API

反射的入口是 `Class` 对象。拿到 `Class` 之后，就能取 `Field`（字段）、`Method`（方法）、`Constructor`（构造器）。

### 2.1 拿到 Class 对象的三种方式

```java
// 1. 类名.class（编译期已知）
Class<Book> c1 = Book.class;

// 2. 实例.getClass()
Book book = new Book();
Class<?> c2 = book.getClass();

// 3. Class.forName（运行期才知道全类名，框架最常用）
Class<?> c3 = Class.forName("com.zx.bookstore.catalog.entity.Book");
```

三种方式拿到的是**同一个 Class 对象**（JVM 里每个类只有一个 Class），`c1 == c2 == c3` 为 `true`。

> Class 对象在类加载阶段由 JVM 创建，存进方法区（JDK 8）/ 元空间（JDK 9+）。它就是「这个类的运行期元数据」。

### 2.2 Field：操作字段

```java
Class<Book> clazz = Book.class;

// 取单个字段
Field title = clazz.getDeclaredField("title");
Field id = clazz.getField("status");   // getField 只能拿 public，含继承

// 取所有字段
Field[] all = clazz.getDeclaredFields(); // 本类声明（含 private），不含继承
Field[] pubs = clazz.getFields();         // public，含继承

// 读写实例字段
Book book = new Book();
title.setAccessible(true);   // private 字段必须先放开
title.set(book, "Redis 实战");
Object val = title.get(book); // "Redis 实战"

// 读写静态字段
Field cache = clazz.getDeclaredField("CACHE");
cache.setAccessible(true);
cache.set(null, new HashMap<>());
```

`getDeclaredField` vs `getField` 是高频面试点：

| | `getField` / `getFields` | `getDeclaredField` / `getDeclaredFields` |
|--|--------------------------|------------------------------------------|
| 范围 | 只 public | 全访问修饰符 |
| 继承 | 含父类 public 字段 | 只本类声明，不含继承 |

### 2.3 Method：调用方法

```java
Class<Book> clazz = Book.class;

// 取方法（要传参数类型，区分重载）
Method setTitle = clazz.getMethod("setTitle", String.class);
Method getTitle = clazz.getDeclaredMethod("getTitle");

// 调用实例方法
Book book = new Book();
setTitle.invoke(book, "Redis 实战");   // 第一个参数是接收者

// 调用静态方法：接收者传 null
Method valueOf = clazz.getMethod("valueOf", int.class);
Object result = valueOf.invoke(null, 1);
```

`invoke` 的两个坑：

1. **第一个参数是接收者**：实例方法传实例，静态方法传 `null`。
2. **参数类型必须精确匹配**：`getMethod("setTitle", CharSequence.class)` 找不到 `setTitle(String)`，因为反射不做自动重载解析。

### 2.4 Constructor：创建实例

```java
Class<Book> clazz = Book.class;

// 无参构造
Book b1 = clazz.getDeclaredConstructor().newInstance();

// 带参构造（要传参数类型）
Constructor<Book> ctor = clazz.getDeclaredConstructor(Long.class, String.class);
Book b2 = ctor.newInstance(1L, "Redis 实战");

// private 构造器（单例破坏）
Constructor<Singleton> sc = Singleton.class.getDeclaredConstructor();
sc.setAccessible(true);
Singleton s = sc.newInstance();  // 绕过 private，单例被破防
```

`newInstance()`（已废弃）和 `Constructor.newInstance()` 的区别：后者能调用带参构造和 private 构造，前者只能无参 public 构造。新代码一律用 `Constructor.newInstance()`。

---

## 三、访问控制：setAccessible 的真相

`private` 字段/方法默认不能反射访问，要先调 `setAccessible(true)`。但这个方法到底干了什么？

### 3.1 它不是「修改修饰符」

`setAccessible(true)` **不会**把字段变成 public，它只是告诉 JVM 的访问检查器：「跳过这次访问的权限校验」。

```java
Field f = Book.class.getDeclaredField("title");
f.setAccessible(true);
// f.getModifiers() 仍是 PRIVATE，修饰符没变
System.out.println(Modifier.isPrivate(f.getModifiers())); // true
```

### 3.2 模块化时代的限制（JDK 9+）

JDK 9 引入模块系统后，`setAccessible(true)` 不再万能：

- 跨模块访问 JDK 内部类（如 `sun.misc.Unsafe`）会被 `--illegal-access` 拦截。
- 用户自己的模块只要 `opens` 给反射访问，就还能用。

所以**反射访问自己写的实体/Service 没问题**，但想反射 `java.lang.String` 内部就会报警告或抛 `InaccessibleObjectException`。

### 3.3 反射破坏单例的经典案例

```java
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();
    private Singleton() {}
    public static Singleton getInstance() { return INSTANCE; }
}

// 反射破防
Constructor<Singleton> c = Singleton.class.getDeclaredConstructor();
c.setAccessible(true);
Singleton fake = c.newInstance();   // 不是 INSTANCE
```

这就是为什么「枚举单例」被推荐——枚举实例由 JVM 保证，反射 `Constructor.newInstance()` 对枚举会直接抛异常，序列化也安全。

---

## 四、动态代理：反射的高阶应用

反射最经典的应用场景是**动态代理**：运行期生成一个实现指定接口的代理类，把方法调用转发给 `InvocationHandler`。

### 4.1 JDK 动态代理

```java
// 接口
public interface BookService {
    BookResponse getBook(Long id);
}

// 真实实现
public class BookServiceImpl implements BookService {
    public BookResponse getBook(Long id) { return repo.findById(id); }
}

// 处理器：所有方法调用都进 invoke
InvocationHandler handler = (proxy, method, args) -> {
    System.out.println("before " + method.getName());
    Object ret = method.invoke(realImpl, args);  // 反射调真实方法
    System.out.println("after  " + method.getName());
    return ret;
};

BookService proxy = (BookService) Proxy.newProxyInstance(
        BookService.class.getClassLoader(),
        new Class[]{BookService.class},
        handler);
proxy.getBook(1L);  // 打印 before / after
```

`Proxy.newProxyInstance` 在运行期用反射生成一个 `$Proxy0` 类，它实现了你传入的接口，每个方法体里都是「调 `handler.invoke`」。

### 4.2 Spring AOP 的本质

Spring AOP 默认就用 JDK 动态代理（目标类实现了接口）或 CGLIB（没实现接口时用字节码生成子类）。你写的 `@Transactional`、`@Async`、`@Cacheable`，底层全是动态代理 + 反射。

```java
// @Transactional 的简化逻辑
@Transactional
public void borrowBook(Long userId, Long bookId) { ... }

// 等价于
TransactionProxy.invoke(() -> {
    transactionManager.begin();
    try {
        target.borrowBook(userId, bookId);  // 反射调原方法
        transactionManager.commit();
    } catch (Exception e) {
        transactionManager.rollback();
        throw e;
    }
});
```

### 4.3 JDK 代理 vs CGLIB

| | JDK 动态代理 | CGLIB |
|--|-------------|-------|
| 原理 | 反射生成接口实现类 | 字节码生成子类 |
| 要求 | 目标必须实现接口 | 目标类不能 final |
| 速度 | 反射调用稍慢 | 字节码调用快，但生成慢 |
| Spring 默认 | 有接口用 JDK | 无接口用 CGLIB |

---

## 五、注解解析：反射的另一半

注解本身不做事，**注解 + 反射**才做事。框架扫描注解、读属性、决定行为。

### 5.1 定义与读取

```java
@Retention(RetentionPolicy.RUNTIME)   // 关键：必须 RUNTIME 才能反射读到
@Target(ElementType.METHOD)
public @interface Tool {
    String name();
    String description() default "";
}

// 反射读注解
Method m = BookSearchTool.class.getMethod("searchBooks", String.class);
if (m.isAnnotationPresent(Tool.class)) {
    Tool t = m.getAnnotation(Tool.class);
    System.out.println(t.name() + " - " + t.description());
}
```

`@Retention` 三个级别：

- `SOURCE`：编译期丢弃（如 `@Override`），反射读不到。
- `CLASS`：写进 class 文件，但 JVM 不加载（默认值），反射读不到。
- `RUNTIME`：运行期可反射读，**框架注解都用这个**。

### 5.2 Spring 怎么用注解 + 反射

```java
// Spring 扫描 @Component 的简化逻辑
for (Class<?> clazz : scan("com.zx")) {
    if (clazz.isAnnotationPresent(Component.class)) {
        Object bean = clazz.getDeclaredConstructor().newInstance();
        context.register(bean);
    }
}
```

`@Autowired`、`@RequestMapping`、`@Value` 全是这套机制。

---

## 六、项目落地：Spring AI Tool Calling 的反射真相

智慧书城 `smart_bookstore` 的 AI 模块里，`BookSearchTool` / `FaqTool` / `BorrowTool` 都标了 `@Tool`：

```java
@Component
public class BookSearchTool {
    @Tool(name = "searchBooks", description = "...")
    public Map<String, Object> searchBooks(@ToolParam("query") String query, ...) { ... }
}
```

大模型（DeepSeek）回复时怎么「调用」这些 Java 方法？**全靠反射**。Spring AI 的 `ToolCallback` 内部流程：

```text
1. 启动时扫描 @Tool 方法 → 用反射读 name/description/参数注解
   → 生成 JSON Schema（告诉大模型「有这些工具可用」）

2. 大模型回复 tool_call: { "name": "searchBooks", "arguments": {"query":"Redis"} }
   → 框架按 name 找到 Method 对象
   → 反射 invoke，把 JSON 参数按 @ToolParam 名字映射到形参
   → 拿到返回值，序列化回喂给大模型

3. 大模型基于工具返回值组织最终回复
```

核心代码（简化）：

```java
// Spring AI 内部：MethodToolCallback 简化版
public class MethodToolCallback {
    private final Method toolMethod;
    private final Object toolBean;       // BookSearchTool 实例

    public String call(String jsonArgs) {
        Map<String, Object> args = parseJson(jsonArgs);
        // 按参数名匹配形参位置
        Object[] params = bindParams(toolMethod, args);
        // 反射调用
        Object result = toolMethod.invoke(toolBean, params);
        return toJson(result);
    }
}
```

**为什么必须反射**：Spring AI 在编译期不知道你会写什么 `@Tool` 方法，只能在运行期扫描注解、按名字调用。这就是「框架用反射」的典型场景。

### 6.1 项目里其他反射痕迹

| 位置 | 反射在做什么 |
|------|-------------|
| Spring IoC | `@Component` 扫描 + `Constructor.newInstance()` 造 Bean |
| `@Autowired` | 反射找字段/构造器注入依赖 |
| MyBatis-Plus | `BaseMapper.selectById` 反射把 ResultSet 列映射到实体字段 |
| `@TableName` / `@TableField` | 反射读注解决定表名/列名 |
| Spring Security `@PreAuthorize` | 反射读注解 + AOP 代理做权限拦截 |
| `@Tool` / `@ToolParam` | Spring AI 反射注册 Tool 给大模型 |

### 6.2 一个「业务里少用反射」的反例

假设你想写一个「通用 CSV 导出」工具，用反射遍历字段：

```java
// 看似优雅，实则脆弱
public String toCsv(Object obj) {
    StringBuilder sb = new StringBuilder();
    for (Field f : obj.getClass().getDeclaredFields()) {
        f.setAccessible(true);
        sb.append(f.get(obj)).append(",");
    }
    return sb.toString();
}
```

问题：

- 字段顺序依赖声明顺序，重命名/插入字段就乱。
- 私有字段被强行读出，可能泄露敏感数据。
- 编译期不报错，重构时 IDE 不会提示。

更好的做法：定义 `CsvWritable` 接口让实体自己实现 `toCsv()`，或用 Jackson CSV 模块（它内部用反射但有缓存和注解控制）。

---

## 七、性能与最佳实践

### 7.1 反射到底慢多少

```text
直接调用           1x
反射调用（无缓存）  ~20x
反射调用（缓存 Method 对象 + setAccessible(true)）  ~3-5x
MethodHandle      ~1.5x
```

`setAccessible(true)` 反而能提速——它跳过了每次调用都做的访问检查。

### 7.2 优化手段

```java
// 差：每次都查方法
for (int i = 0; i < 10000; i++) {
    clazz.getMethod("setTitle", String.class).invoke(obj, "x");
}

// 好：缓存 Method 对象
Method m = clazz.getMethod("setTitle", String.class);
m.setAccessible(true);
for (int i = 0; i < 10000; i++) {
    m.invoke(obj, "x");
}

// 更好：JDK 7+ MethodHandle（可被 JIT 内联）
MethodHandle mh = MethodHandles.lookup().findVirtual(clazz, "setTitle",
        MethodType.methodType(void.class, String.class));
mh.invoke(obj, "x");
```

### 7.3 何时该用 / 不该用

| 该用 | 不该用 |
|------|--------|
| 写框架（IoC / ORM / 序列化） | 业务逻辑里调一个已知类的方法 |
| 工具类（BeanUtils / JSON 映射） | 用反射绕过 private 修改第三方库内部状态 |
| 注解驱动配置 | 用反射代替 if-else 做分发 |
| 运行期才知道类名的场景 | 性能敏感的热路径 |

---

## 八、常见面试题速答

**Q1：反射能破坏单例吗？**
能。`private` 构造器 `setAccessible(true)` 后可 `newInstance`。枚举单例防得住，因为 JVM 不允许反射创建枚举实例。

**Q2：`Class.forName` 和 `类名.class` 的区别？**
前者触发类初始化（执行 static 块），后者不触发。`实例.getClass()` 也不触发。JDBC 加载驱动用 `Class.forName` 就是为了触发 `Driver` 的 static 注册。

**Q3：反射调用私有方法为什么不会破坏封装？**
其实会破坏。`setAccessible(true)` 是「明确声明我要破坏封装」，JDK 9+ 模块系统会进一步限制。框架用反射是约定，业务乱用是滥用。

**Q4：动态代理为什么必须实现接口？**
JDK 动态代理用 `Proxy.newProxyInstance` 生成的是「实现指定接口的类」，所以目标必须有接口。没接口就用 CGLIB 生成子类。

**Q5：反射和注解什么关系？**
注解是元数据，反射是「读元数据的手段」。没有反射，注解只是注释；有了反射，注解才能驱动框架行为。

---

## 九、总结

反射的本质是：**把「类、字段、方法、构造器」从编译期的语法概念，变成运行期可操作的数据**。它让框架能在「不知道用户写什么」的前提下调用用户的代码，是 Spring / MyBatis / Jackson / Spring AI 的共同基石。

记住三条主线：

1. **入口是 `Class`**：`类名.class` / `实例.getClass()` / `Class.forName`。
2. **四大 API**：`Field` 读写字段、`Method` 调方法、`Constructor` 造对象、注解 API 读元数据。
3. **代价是性能 + 封装 + 可读性**：框架用得，业务慎用，热路径缓存 `Method` 对象或换 `MethodHandle`。

回到智慧书城：你写的 `@Tool` 方法能被大模型调用，背后正是 Spring AI 用反射扫描注解、按名字 `invoke` 的结果。理解了反射，你才算真正理解了「为什么 Spring 那么多注解能自动生效」。

---

## 十、相关文档

| 文档 | 说明 |
|------|------|
| [四层架构学习文档](./四层架构学习文档.md) | Controller/Service/Repository/Entity 分层，反射在各层的作用 |
| [项目介绍](./项目介绍.md) | 智慧书城整体能力一览 |
| [AI模块分板块实施流程](./AI模块分板块实施流程.md) | Spring AI Tool Calling 实施流程 |
| [接口文档](./接口文档.md) | AI / 书城接口规范 |
