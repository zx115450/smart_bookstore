# Spring Boot 框架底层架构学习博客

> 读者画像：已经能熟练使用 Spring Boot（写业务、配 YAML、加 Starter），但启动链、自动配置、条件装配等「为什么能这样用」仍偏模糊。  
> 文中代码：摘自 / 精简自 Spring Boot 4.x（`main`）源码思路，删去次要分支与日志，便于阅读；行号随版本会变，以类名为准。  
> 配套短文：[docs/modules/spring-boot.md](../modules/spring-boot.md)

本文目标不是再讲「怎么新建一个 Controller」，而是把 **Spring Boot 在 Spring Framework 之上加了什么、启动时按什么顺序做事、自动配置如何发现与过滤** 讲清楚。不依赖你本地打开源码仓，也能跟着代码把主链路走通。

---

## 一、先建立正确心智模型

### 1.1 Spring Framework 与 Spring Boot 的分工

可以把关系理解成：

```text
你的业务代码（Controller / Service / @Configuration）
        │
        ▼
Spring Framework（IoC / AOP / MVC / Transaction …）
        │  ← ApplicationContext、Bean 生命周期、依赖注入
        ▼
Spring Boot（约定、自动配置、启动编排、嵌入式容器、外部化配置增强）
        │  ← SpringApplication、AutoConfiguration、Starter
        ▼
运行时（JVM + 内嵌 Tomcat/Netty + 中间件客户端）
```

- **Spring Framework**：容器与编程模型。没有 Boot，你也能手写 `@Configuration`、XML、自己起 Tomcat。
- **Spring Boot**：在 Framework 之上做三件事：
  1. **编排启动**（`SpringApplication.run`）
  2. **按约定装配基础设施 Bean**（自动配置）
  3. **用 Starter 聚合依赖 + 默认配置**（开箱即用）

因此：你会用 Boot，本质是「会用 Framework + 享受了约定」；要懂底层，必须同时理解 **容器刷新（`refresh`）** 与 **Boot 在刷新前后插入的扩展点**。

你每天写的入口，其实只有这一行真正「启动宇宙」：

```java
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

后面整篇文章，都在回答：`run` 进去之后发生了什么。

### 1.2 「约定优于配置」到底约定了什么

| 约定 | 含义 |
| --- | --- |
| 主类包扫描 | `@SpringBootApplication` 所在包及其子包默认被 `@ComponentScan` |
| Starter 即能力 | 引入 `spring-boot-starter-web` 即倾向 Servlet Web + Jackson + 内嵌容器 |
| 配置键命名空间 | `spring.datasource.*`、`server.port` 等由 `@ConfigurationProperties` 绑定 |
| 条件装配 | classpath / 已有 Bean / 属性值决定某个自动配置是否生效 |
| 可覆盖 | 你自己定义同类型 Bean，通常因 `@ConditionalOnMissingBean` 而「赢过」默认 |

面试口径：**Boot 不是魔法，是「候选配置类列表 + 条件过滤 + 延迟导入」叠在 Spring 的 `@Import` 机制上。**

---

## 二、整体架构分层（从外到内）

| 层次 | 典型类 / 资源 | 职责 |
| --- | --- | --- |
| 启动编排 | `SpringApplication` | `main` 入口、环境、上下文创建、刷新、Runner |
| 自动配置 | `AutoConfigurationImportSelector`、`*AutoConfiguration` | 发现候选、过滤、导入 |
| 配置绑定 | `Binder`、`@ConfigurationProperties` | YAML / 环境变量 → Java 对象 |
| 嵌入式 Web | `ServletWebServerFactory` 相关自动配置 | 内嵌 Tomcat 等 |
| Starter | 几乎空的 `pom` / Gradle 依赖 | 聚合坐标 |
| 清单文件 | `META-INF/spring/...AutoConfiguration.imports` | 登记自动配置类全名 |

---

## 三、入口注解：`@SpringBootApplication` 拆解

源码里它就是三个注解叠在一起（精简如下）：

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration          // 1. 本质是 @Configuration
@EnableAutoConfiguration          // 2. 打开自动配置
@ComponentScan(excludeFilters = { // 3. 扫描组件
    @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public @interface SpringBootApplication {

    @AliasFor(annotation = EnableAutoConfiguration.class)
    Class<?>[] exclude() default {};

    @AliasFor(annotation = EnableAutoConfiguration.class)
    String[] excludeName() default {};

    @AliasFor(annotation = ComponentScan.class, attribute = "basePackages")
    String[] scanBasePackages() default {};
}
```

所以「一个注解搞定」只是语法糖，语义仍是：**配置类 + 组件扫描 + 导入自动配置选择器**。

### 3.1 `@SpringBootConfiguration`

标记「这是 Boot 应用的配置类」。主类本身就是 `@Configuration`，可以在上面写 `@Bean`。

### 3.2 `@ComponentScan`

默认从 **主类所在包** 向下扫描。排除过滤器避免把自动配置类误当普通组件扫进来。

实践：主类包不要过浅（乱扫依赖包），也不要过深（扫不到业务模块）。

### 3.3 `@EnableAutoConfiguration`：真正打开自动配置的开关

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)  // 关键：导入选择器
public @interface EnableAutoConfiguration {

    String ENABLED_OVERRIDE_PROPERTY = "spring.boot.enableautoconfiguration";

    Class<?>[] exclude() default {};

    String[] excludeName() default {};
}
```

开启自动配置 = **`@Import` 一个 `DeferredImportSelector`**。装哪些类的逻辑在 `AutoConfigurationImportSelector`，不在注解本身。

总开关（几乎只用于排障）：

```yaml
spring.boot.enableautoconfiguration: false
```

### 3.4 `exclude` / `excludeName`

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class DemoApplication { }
```

或配置方式：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

类可能不在 classpath 时，优先用 `excludeName`（字符串），尤其当注解被二次封装成元注解时。

---

## 四、启动总流程：`SpringApplication.run` 逐段拆解

### 4.1 你调用的静态入口

```java
public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
    return run(new Class<?>[] { primarySource }, args);
}

public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
    return new SpringApplication(primarySources).run(args);
}
```

先 `new SpringApplication`（推断 Web 类型、准备监听器等），再进实例方法 `run`。

### 4.2 总览图

```text
SpringApplication.run(primarySource, args)
        │
        ├─ createBootstrapContext()
        ├─ listeners.starting(...)
        ├─ prepareEnvironment(...)
        ├─ printBanner(...)
        ├─ createApplicationContext()
        ├─ prepareContext(...)
        ├─ refreshContext(context)      ★ Spring refresh
        ├─ afterRefresh(...)
        ├─ listeners.started(...)
        ├─ callRunners(...)
        └─ listeners.ready(...)
```

### 4.3 实例 `run`：把整条链写在一个方法里

下面是按源码结构精简后的「可读版」（省略 AOT / 计时等细节）：

```java
public ConfigurableApplicationContext run(String... args) {
    DefaultBootstrapContext bootstrapContext = createBootstrapContext();
    ConfigurableApplicationContext context = null;
    SpringApplicationRunListeners listeners = getRunListeners(args);
    listeners.starting(bootstrapContext, this.mainApplicationClass);
    try {
        ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
        ConfigurableEnvironment environment =
                prepareEnvironment(listeners, bootstrapContext, applicationArguments);
        Banner printedBanner = printBanner(environment);

        context = createApplicationContext();
        prepareContext(bootstrapContext, context, environment,
                listeners, applicationArguments, printedBanner);

        refreshContext(context);          // → context.refresh()
        afterRefresh(context, applicationArguments);

        listeners.started(context, /* startup duration */);
        callRunners(context, applicationArguments);
    }
    catch (Throwable ex) {
        throw handleRunFailure(context, ex, listeners);
    }
    try {
        if (context.isRunning()) {
            listeners.ready(context, /* ready duration */);
        }
    }
    catch (Throwable ex) {
        throw handleRunFailure(context, ex, null);
    }
    return context;
}
```

四大块：

1. Boot 编排：环境、监听器、创建 Context  
2. `load` 主配置类  
3. `refresh`：扫描、自动配置导入、Bean 创建、内嵌服务器  
4. Runner + ready  

### 4.4 构造阶段：Web 类型从哪来

构造 `SpringApplication` 时会根据 classpath **推断** `WebApplicationType`：

| 类型 | 大致条件（直觉） |
| --- | --- |
| `SERVLET` | 有 Servlet + DispatcherServlet 一类 |
| `REACTIVE` | 有 WebFlux 的 `DispatcherHandler` 等 |
| `NONE` | 都不是 → 非 Web |

这决定后面创建哪种 `ApplicationContext`。所以「只加了 `webflux` starter」会变成响应式应用，不是巧合。

### 4.5 `prepareEnvironment`：配置何时就位

```java
private ConfigurableEnvironment prepareEnvironment(
        SpringApplicationRunListeners listeners,
        DefaultBootstrapContext bootstrapContext,
        ApplicationArguments applicationArguments) {

    ConfigurableEnvironment environment = getOrCreateEnvironment();
    configureEnvironment(environment, applicationArguments.getSourceArgs());
    ConfigurationPropertySources.attach(environment);   // 松散绑定

    listeners.environmentPrepared(bootstrapContext, environment);

    bindToSpringApplication(environment);               // spring.main.* → SpringApplication
    // ... 必要时转换 Environment 类型 ...
    ConfigurationPropertySources.attach(environment);
    return environment;
}
```

重点：

1. 命令行、系统属性、`application.yaml` / `application-{profile}.yaml` 等进入 `PropertySource`  
2. `ConfigurationPropertySources.attach` 让 `spring.datasource.url` 与 `SPRING_DATASOURCE_URL` 能对上  
3. `bindToSpringApplication` 绑定 `spring.main.banner-mode`、`lazy-initialization` 等  

优先级心智（高 → 低）：

```text
命令行参数
  > 系统属性 / 环境变量
  > application-{profile}.yaml
  > application.yaml
  > 默认属性
```

所以 `--server.port=9090` 能盖过 YAML。

业务侧监听环境就绪示例：

```java
@Component
public class EnvReadyListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        // 此处 Context 里业务 Bean 通常还没有；适合改 Environment，不适合注入 Service
        String port = event.getEnvironment().getProperty("server.port");
    }
}
```

### 4.6 `prepareContext`：先挂上主类，还不到「万物皆 Bean」

```java
private void prepareContext(..., ConfigurableApplicationContext context,
        ConfigurableEnvironment environment, ..., ApplicationArguments applicationArguments,
        Banner printedBanner) {

    context.setEnvironment(environment);
    applyInitializers(context);
    listeners.contextPrepared(context);

    beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
    if (printedBanner != null) {
        beanFactory.registerSingleton("springBootBanner", printedBanner);
    }

    // 把 @SpringBootApplication 主类注册成配置类 BeanDefinition
    Set<Object> sources = getAllSources();
    load(context, sources.toArray(new Object[0]));

    listeners.contextLoaded(context);
}
```

此时自动配置类 **通常还没全部进来**；它们多半在随后 `refresh` 里，作为 **Deferred Import** 被导入。

### 4.7 `refreshContext` → `refresh`

```java
private void refreshContext(ConfigurableApplicationContext context) {
    if (this.properties.isRegisterShutdownHook()) {
        shutdownHook.registerApplicationContext(context);
    }
    refresh(context); // 最终调用 AbstractApplicationContext.refresh()
}
```

对 Boot 用户最相关的 `refresh` 直觉：

```text
refresh()
  ├─ 准备 BeanFactory
  ├─ 执行 BeanFactoryPostProcessor
  │     └─ ConfigurationClassPostProcessor
  │           ├─ 解析 @Configuration / @ComponentScan
  │           ├─ 处理 @Import
  │           └─ 处理 DeferredImportSelector  ← 自动配置在这里进来
  ├─ 注册 BeanPostProcessor
  ├─ 实例化非懒加载单例
  │     └─ DataSource、Redis、Tomcat 等在此创建
  └─ （Web）启动内嵌服务器
```

**一句话**：自动配置不是 `run()` 里 for 循环 `new` 出来的，而是配置类解析时延迟导入，再在单例实例化阶段真正创建对象。

### 4.8 Runner：容器就绪后的回调

```java
@Component
@Order(1)
public class WarmUpRunner implements ApplicationRunner {

    private final BookService bookService;

    public WarmUpRunner(BookService bookService) {
        this.bookService = bookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bookService.warmUpCache(); // 此时注入业务 Bean 已安全
    }
}
```

`CommandLineRunner` 类似，参数是原始 `String[]`。二者都在 `listeners.started` 之后、`ready` 前后由 `callRunners` 调用。

---

## 五、自动配置：Boot 的「灵魂」机制

### 5.1 候选列表：`.imports` 文件长什么样

Boot 3+ 主路径不再靠 `spring.factories` 列 `EnableAutoConfiguration`，而是每个 jar 带：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

文件内容示例（示意，真实列表更长）：

```text
org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

一行一个全限定类名，`#` 开头为注释。

### 5.2 `ImportCandidates`：如何扫到所有 jar 里的清单

```java
public final class ImportCandidates {

    private static final String LOCATION = "META-INF/spring/%s.imports";

    public static ImportCandidates load(Class<?> annotation, ClassLoader classLoader) {
        String location = String.format(LOCATION, annotation.getName());
        // 例如：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
        Enumeration<URL> urls = findUrlsInClasspath(classLoader, location);
        List<String> importCandidates = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            importCandidates.addAll(readCandidateConfigurations(url));
        }
        return new ImportCandidates(importCandidates);
    }
}
```

**ClassLoader 枚举 classpath 上所有同名资源** → 合并所有 Starter / 第三方 jar 登记的自动配置类。这就是「加依赖就多一批候选」的根因。

### 5.3 `AutoConfigurationImportSelector`：选谁、踢谁

```java
@Override
public String[] selectImports(AnnotationMetadata annotationMetadata) {
    if (!isEnabled(annotationMetadata)) {
        return NO_IMPORTS;
    }
    AutoConfigurationEntry entry = getAutoConfigurationEntry(annotationMetadata);
    return StringUtils.toStringArray(entry.getConfigurations());
}

protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata metadata) {
    AnnotationAttributes attributes = getAttributes(metadata);
    List<String> configurations = getCandidateConfigurations(metadata, attributes);
    configurations = removeDuplicates(configurations);

    Set<String> exclusions = getExclusions(metadata, attributes); // exclude / excludeName / 配置
    configurations.removeAll(exclusions);

    configurations = getConfigurationClassFilter().filter(configurations); // 条件快速过滤
    fireAutoConfigurationImportEvents(configurations, exclusions);
    return new AutoConfigurationEntry(configurations, exclusions);
}

protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
        AnnotationAttributes attributes) {
    ImportCandidates importCandidates =
            ImportCandidates.load(this.autoConfigurationAnnotation, getBeanClassLoader());
    return importCandidates.getCandidates();
}
```

流水线一句话：

```text
读 .imports 全部候选 → 去重 → 去掉 exclude → 条件过滤 → 得到真正要 @Import 的类名
```

启动时加：

```bash
java -jar app.jar --debug
```

或：

```yaml
debug: true
```

控制台会打出 **Positive matches / Negative matches**，就是这条流水线的人话版。

### 5.4 为什么是 `DeferredImportSelector`

`AutoConfigurationImportSelector` 实现 `DeferredImportSelector`，并提供 `AutoConfigurationGroup`：

- **延迟**：先解析你自己的 `@Configuration` / `@Bean`，再导入自动配置  
- **可排序**：`@AutoConfigureBefore` / `@AutoConfigureAfter` / `@AutoConfiguration(before/after)`  

这样才能让 `@ConditionalOnMissingBean` 看见「你已经声明过的 DataSource」。

### 5.5 条件注解：开关长什么样

```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)  // 真正干活的是 Condition 实现
public @interface ConditionalOnClass {

    Class<?>[] value() default {};

    String[] name() default {};
}
```

| 注解 | 含义 | 典型用途 |
| --- | --- | --- |
| `@ConditionalOnClass` | classpath 有类 | 有 Redis 客户端才装配 Redis |
| `@ConditionalOnMissingBean` | 容器还没有该 Bean | **允许用户覆盖默认** |
| `@ConditionalOnProperty` | 属性匹配 | `xxx.enabled=true` |
| `@ConditionalOnWebApplication` | Web 类型 | Servlet / Reactive 分支 |

官方建议：需要 `@ConditionalOnClass` 时，用 **静态内部配置类** 隔离，避免在 `@Bean` 方法签名上过早加载缺失类：

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

### 5.6 「可覆盖」：最小可运行对照

自动配置（框架侧示意）：

```java
@AutoConfiguration
@ConditionalOnClass(XxxClient.class)
@EnableConfigurationProperties(XxxProperties.class)
public class XxxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public XxxClient xxxClient(XxxProperties properties) {
        return new XxxClient(properties.getEndpoint());
    }
}
```

业务侧覆盖：

```java
@Configuration
public class MyXxxConfig {

    @Bean
    public XxxClient xxxClient() {
        return new XxxClient("https://custom.example.com");
    }
}
```

因为用户配置先解析，容器里已有 `XxxClient` → `@ConditionalOnMissingBean` 失败 → 框架默认 Bean 不创建。

### 5.7 真实范例：`DataSourceAutoConfiguration`（精简）

Boot 4.x 中数据源自动配置大致是这样组织的（包名随模块拆分可能为 `jdbc.autoconfigure`）：

```java
@AutoConfiguration(before = DataSourceInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@EnableConfigurationProperties(DataSourceProperties.class)
@Import({ /* pool metadata 等 */ })
public final class DataSourceAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(PooledDataSourceCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import({
        DataSourceConfiguration.Hikari.class,
        DataSourceConfiguration.Tomcat.class,
        DataSourceConfiguration.Dbcp2.class
        // ...
    })
    protected static class PooledDataSourceConfiguration {

        @Bean
        @ConditionalOnMissingBean(JdbcConnectionDetails.class)
        PropertiesJdbcConnectionDetails jdbcConnectionDetails(DataSourceProperties properties) {
            return new PropertiesJdbcConnectionDetails(properties);
        }
    }
}
```

读这段时抓住三件事：

1. **类路径条件**：没有 JDBC，整条自动配置不进来  
2. **MissingBean**：你自己提供了 `DataSource`，池化默认配置让路  
3. **Properties**：`spring.datasource.*` 绑到 `DataSourceProperties` 再参与创建  

### 5.8 Redis Starter 故事（对照上面的代码）

```text
1. 引入 starter-data-redis → 依赖进客户端 + 自动配置类进 .imports
2. ImportCandidates 合并候选，包含 RedisAutoConfiguration
3. @ConditionalOnClass 通过
4. 你没自定义 RedisConnectionFactory → @ConditionalOnMissingBean 通过
5. 绑定 spring.data.redis.* → 创建连接与模板 Bean
6. @Autowired RedisTemplate 成功
```

---

## 六、外部化配置与 `@ConfigurationProperties`

### 6.1 业务里推荐这样写

```java
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private int rateLimitPerUserHourly = 30;
    private int sessionTtlMinutes = 30;

    // getters / setters
}
```

```java
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig { }
```

```yaml
app:
  ai:
    rate-limit-per-user-hourly: 30
    session-ttl-minutes: 30
```

松散绑定下，下面这些往往能对上同一个字段：

```text
app.ai.rate-limit-per-user-hourly
APP_AI_RATE_LIMIT_PER_USER_HOURLY
app.ai.rateLimitPerUserHourly
```

### 6.2 和自动配置的关系

```text
Environment（YAML / 环境变量 / 命令行）
        │ Binder
        ▼
XxxProperties（@ConfigurationProperties）
        │ 注入
        ▼
XxxAutoConfiguration.@Bean(...) 使用 Properties 创建组件
```

`@Value("${x.y}")` 适合零散取值；模块级配置优先 Properties，便于校验与 IDE 补全（`configuration-processor` 生成元数据）。

---

## 七、Starter：依赖干净的原因

### 7.1 模块怎么拆

```text
xxx-spring-boot-autoconfigure   // 自动配置 + 条件 + Properties + .imports
xxx-spring-boot-starter         // 几乎无代码，只声明依赖
```

Starter 的 Gradle / Maven 依赖示意：

```xml
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>xxx-spring-boot-autoconfigure</artifactId>
  </dependency>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>xxx-client</artifactId>
  </dependency>
</dependencies>
```

### 7.2 自己写 Starter 时最少要有的文件

自动配置类 + 清单：

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容：

```text
com.example.xxx.autoconfigure.XxxAutoConfiguration
```

用 `ApplicationContextRunner` 测三种分支（有类 / 无类 / 用户已提供 Bean）比「感觉能跑」可靠得多。

---

## 八、内嵌 Web：请求怎么进来

```text
内嵌 Tomcat（WebServer）
        │
        ▼
DispatcherServlet（自动配置注册到 `/`）
        │
        ▼
HandlerMapping → @RestController → HttpMessageConverter
```

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/{id}")
    public BookDetail get(@PathVariable long id) {
        return bookService.getDetail(id);
    }
}
```

端口来自绑定：

```yaml
server:
  port: 8081
```

`refresh` 后期启动 WebServer；端口冲突等错误会让整个启动失败。  
业务 MVC 一般不必直接碰 Netty API；长连接 / 网关 / 自研协议才下沉到 Netty。

---

## 九、和 Spring 容器生命周期的咬合

| Boot 概念 | Framework 落点 |
| --- | --- |
| `@SpringBootApplication` | `@Configuration` + `@ComponentScan` + `@Import` |
| 自动配置类 | 被导入的 `@Configuration` |
| `@ConditionalOn*` | `@Conditional(Condition)` |
| `ApplicationRunner` | 启动完成后的 Boot 回调 |
| `Environment` | 标准 Environment + Boot 属性源 |

Bean 生命周期仍是：

```text
实例化 → 填充属性 → Aware → BeanPostProcessor 前
→ @PostConstruct / InitializingBean → BeanPostProcessor 后 → 可用
→ 销毁回调
```

事务、AOP 代理发生在后置处理器阶段；Boot 只是先把 `DataSource`、`PlatformTransactionManager` 等配好。

---

## 十、不打开源码仓也能做的三天练习

### Day 1：启动链

1. 主类对 `SpringApplication.run` 下断点，Step Into  
2. 观察是否经过：`prepareEnvironment` → `createApplicationContext` → `prepareContext` → `refresh`  
3. 在 `refresh` 相关调用栈里留意 `ConfigurationClassPostProcessor`

### Day 2：自动配置

1. `--debug` 启动，保存 Positive / Negative  
2. 对一个熟悉组件（DataSource / Redis）在业务里写覆盖 `@Bean`，再看报告变化  
3. 用 `exclude` 去掉自动配置，观察失败模式

### Day 3：绑定

1. 改 YAML，在自己的 `@ConfigurationProperties` 对象断点看字段  
2. 用环境变量覆盖同一键，确认松散绑定  

若你有本目录 `spring-boot` 仓，可再搜索：`SpringApplication`、`AutoConfigurationImportSelector`、`ImportCandidates`。

---

## 十一、常见误区

1. **Boot 替换了 Spring** → 否，容器仍是 Spring。  
2. **自动配置会扫整个磁盘** → 否，只加载各 jar 的 `.imports` 候选再过滤。  
3. **所有 Bean 在 `main` 返回前都创建完** → 懒加载 / `@Lazy` 会推迟。  
4. **新版本还在背 `spring.factories` 列 EnableAutoConfiguration** → Boot 3+ 主路径看 `.imports`。  
5. **密钥写进默认 yaml 提交** → 绑定不区分来源，安全是工程纪律。  

排障顺序：`--debug` → 条件为何 Negative → 是否 exclude → 是否用户 Bean 抢占 → 配置键是否绑错。

---

## 十二、面试 3 分钟讲法

1. **定位**：Boot = 启动编排 + 自动配置 + Starter；容器是 Spring。  
2. **启动**：`run` → Environment → Context → `refresh` → Runner → ready。  
3. **自动配置**：`.imports` 候选 → `DeferredImportSelector` 延迟导入 → `@Conditional` 过滤 → `@ConditionalOnMissingBean` 可覆盖。  
4. **举例**：口述 DataSource / Redis 如何从 Starter 变成可注入 Bean。  
5. **验证**：`--debug` 与断点，不靠背类名。  

---

## 十三、自测题

### 题目

1. `@SpringBootApplication` 合成了哪三个注解？`@EnableAutoConfiguration` 的 `@Import` 导入了谁？
2. 为什么自动配置要用 `DeferredImportSelector`？
3. `.imports` 文件路径是什么？`ImportCandidates.load` 如何合并多 jar？
4. 自己提供 `DataSource` Bean 后，为何默认池化自动配置往往不再生效？
5. `prepareEnvironment` 与 `refresh` 谁先？Runner 在哪之后？
6. Starter 与 autoconfigure 模块如何分工？清单文件写在哪？

### 参考答案

**1.** 三个注解：`@SpringBootConfiguration`（本质是 `@Configuration`）、`@EnableAutoConfiguration`、`@ComponentScan`。  
`@EnableAutoConfiguration` 通过 `@Import` 导入的是 **`AutoConfigurationImportSelector`**（`DeferredImportSelector` 实现）；装哪些类的逻辑在 Selector 里，不在注解本身。

**2.** 为了 **延迟导入**：先解析用户自己的 `@Configuration` / `@Bean`，再导入自动配置。这样 `@ConditionalOnMissingBean` 才能看见「你已经声明过的 Bean」（例如自建 `DataSource`）。同时便于按 `@AutoConfigureBefore` / `@After`（以及 `@AutoConfiguration(before/after)`）排序。

**3.** Boot 3+ 主路径：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`ImportCandidates.load` 用 `META-INF/spring/%s.imports`（`%s` 为注解全名）定位资源；`ClassLoader` **枚举 classpath 上所有同名 URL**（每个 jar 一份），逐个读入候选类名并合并，后续再去重、exclude、条件过滤。

**4.** 池化相关配置带有 `@ConditionalOnMissingBean({ DataSource.class, XADataSource.class })`。用户已提供 `DataSource` → 条件失败 → Hikari / Tomcat / Dbcp2 等默认池化自动配置让路，不再创建默认 `DataSource`。

**5.** **`prepareEnvironment` 在 `refresh` 之前**。顺序直觉：

```text
prepareEnvironment → … → refresh → listeners.started → callRunners → ready
```

`ApplicationRunner` / `CommandLineRunner` 在 **`refresh` 完成且 `started` 之后**，由 `callRunners` 调用。

**6.** 分工：

| 模块 | 职责 |
| --- | --- |
| `xxx-spring-boot-autoconfigure` | 自动配置类、条件、Properties、**.imports 清单** |
| `xxx-spring-boot-starter` | 几乎无业务代码，只聚合依赖（autoconfigure + 客户端等） |

清单写在 autoconfigure 模块：

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

一行一个自动配置类全限定名。

---

## 十四、延伸阅读

| 资源 | 用途 |
| --- | --- |
| [docs/modules/spring-boot.md](../modules/spring-boot.md) | 短路径与外链 |
| [学习路线.md](../../学习路线.md) | 整体路线 |
| [SpringApplication 官方文档](https://docs.spring.io/spring-boot/reference/features/spring-application.html) | 启动与定制 |
| [Developing Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) | 自写自动配置 |
| [JavaGuide 自动装配](https://javaguide.cn/system-design/framework/spring/spring-boot-auto-assembly-principles.html) | 中文体系化 |

---

## 结语

熟练使用 Spring Boot，说明你已站在约定上把业务跑通。底层可以收成一句：

**`SpringApplication` 编排启动 + `.imports` 列出候选 + `DeferredImportSelector` 延迟导入 + `@Conditional*` 过滤 + Starter 聚合依赖 + `Environment` 绑定属性。**

把文中的 `run`、`getAutoConfigurationEntry`、`ImportCandidates.load` 与「用户 `@Bean` 覆盖默认」对照着走一遍，Boot 就不再是黑盒，而是可预测的工程框架。
