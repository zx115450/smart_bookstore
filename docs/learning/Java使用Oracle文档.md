# Java 使用 Oracle 文档

> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（Java 接入 Oracle）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：[Oracle 使用文档](./Oracle使用文档.md) · [MySQL 与 Oracle 保证 CP 面试文档](./MySQL与Oracle保证CP面试文档.md)  
> **说明**：本仓库运行时以 **MySQL + MyBatis-Plus** 为主；本文讲 Java / JDBC / Spring Boot 如何连接与操作 Oracle，便于课堂实验、面试与技术选型对照。

---

## 目录

1. [学习目标](#1-学习目标)
2. [整体链路](#2-整体链路)
3. [依赖与驱动](#3-依赖与驱动)
4. [连接串（JDBC URL）](#4-连接串jdbc-url)
5. [纯 JDBC 入门](#5-纯-jdbc-入门)
6. [事务](#6-事务)
7. [调用存储过程](#7-调用存储过程)
8. [Spring Boot 接入](#8-spring-boot-接入)
9. [MyBatis / MyBatis-Plus 要点](#9-mybatis--mybatis-plus-要点)
10. [类型与 SQL 差异（相对 MySQL）](#10-类型与-sql-差异相对-mysql)
11. [分页、主键与常见写法](#11-分页主键与常见写法)
12. [连接池与生产注意](#12-连接池与生产注意)
13. [与本仓库的对照](#13-与本仓库的对照)
14. [常见问题](#14-常见问题)
15. [能力自检](#15-能力自检)

---

## 1. 学习目标

学完后应能：

1. 在 Maven / Gradle 中引入 Oracle JDBC 驱动并写对连接 URL
2. 用 JDBC 完成查询、更新、事务提交 / 回滚
3. 用 `CallableStatement` 调用存储过程（含出参）
4. 在 Spring Boot 中配置 `DataSource`，理解与 MySQL 配置的差异点
5. 说出 Oracle 与 MySQL 在类型、分页、自增主键上的常见坑

预计投入：半天～1 天（含本地连库跑通 CRUD）。

---

## 2. 整体链路

```text
Java 应用
  → JDBC API（DriverManager / DataSource）
    → Oracle JDBC 驱动（ojdbc）
      → 网络（通常 Thin 模式，端口 1521）
        → Oracle 监听器 Listener
          → 实例 → 数据库（用户 Schema 下的表 / 过程）
```

和客户端工具（SQL\*Plus、PL/SQL Developer）走的是同一套库，只是入口从「人手写 SQL」换成「程序通过驱动发 SQL」。

权限模型不变：应用应使用 **业务用户**，不要用 `SYS` / `SYSTEM` 做业务连接。详见 [Oracle 使用文档](./Oracle使用文档.md)。

---

## 3. 依赖与驱动

### 3.1 Maven（推荐）

较新写法（Maven Central 上的官方坐标，版本按环境选择）：

```xml
<dependency>
  <groupId>com.oracle.database.jdbc</groupId>
  <artifactId>ojdbc11</artifactId>
  <version>21.9.0.0</version>
</dependency>
```

| 工件 | 适用 |
| --- | --- |
| `ojdbc11` | JDK 11+ |
| `ojdbc8` | JDK 8 |

老环境也可能看到 `com.oracle.ojdbc:ojdbc8` 或本地安装 `ojdbc8.jar`，课堂以老师提供的坐标 / jar 为准。

### 3.2 驱动类名

```text
oracle.jdbc.OracleDriver
```

JDBC 4+ 一般会自动加载，无需再写 `Class.forName`；面试仍可能问到这个类名。

---

## 4. 连接串（JDBC URL）

### 4.1 两种常见格式

**Service Name（12c+ PDB 常用，推荐先会这个）：**

```text
jdbc:oracle:thin:@//主机:1521/服务名
```

示例：

```text
jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1
jdbc:oracle:thin:@//192.168.1.10:1521/orcl
```

**SID（老库常见）：**

```text
jdbc:oracle:thin:@主机:1521:SID
```

示例：

```text
jdbc:oracle:thin:@127.0.0.1:1521:ORCL
```

注意：`@//host:port/service` 与 `@host:port:sid` **斜杠数量和分隔符不同**，写错会直接连不上。

### 4.2 Thin vs OCI

| 模式 | 说明 |
| --- | --- |
| **Thin** | 纯 Java，无需装 Oracle 客户端，应用最常用 |
| **OCI** | 依赖本机 Oracle Client，特殊场景才用 |

学习与 Spring Boot 默认都用 **Thin**。

### 4.3 用户与 Schema

连接用户即默认 Schema。查 `emp` 时等价于当前用户下的表；跨用户需 `其他用户.表名` 且已授权。

---

## 5. 纯 JDBC 入门

### 5.1 获取连接

```java
String url = "jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1";
String user = "app_user";
String password = "YourPassword1";

try (Connection conn = DriverManager.getConnection(url, user, password)) {
    System.out.println("connected: " + !conn.isClosed());
}
```

### 5.2 查询

```java
String sql = "SELECT empno, ename, sal FROM emp WHERE deptno = ?";

try (Connection conn = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = conn.prepareStatement(sql)) {

    ps.setInt(1, 10);

    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            long empno = rs.getLong("empno");
            String ename = rs.getString("ename");
            // Oracle NUMBER 常用 getBigDecimal / getDouble，按精度选择
            System.out.println(empno + " " + ename);
        }
    }
}
```

务必用 **`PreparedStatement`**，避免字符串拼接 SQL（防注入，也利于驱动侧处理类型）。

### 5.3 插入 / 更新

```java
String sql = "INSERT INTO book(id, title) VALUES (?, ?)";

try (Connection conn = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = conn.prepareStatement(sql)) {

    ps.setLong(1, 1001L);
    ps.setString(2, "Oracle 入门");
    int rows = ps.executeUpdate();
    conn.commit(); // 若 autoCommit=false
}
```

Oracle 连接在部分环境下默认 `autoCommit=true`（与工具、连接池配置有关）。业务代码应 **显式管理事务**，不要假设与 SQL\*Plus 行为完全一致。

---

## 6. 事务

```java
Connection conn = DriverManager.getConnection(url, user, password);
conn.setAutoCommit(false);
try {
    // 多条 DML
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
} finally {
    conn.close();
}
```

Spring 中通常用 `@Transactional`，由 `DataSourceTransactionManager` / `JpaTransactionManager` 管理，底层仍是连接的 `commit` / `rollback`。

与库内机制的关系（概念）：

- 应用 `COMMIT` → 驱动发提交 → 库侧保证相关 **redo** 落盘等（持久性）
- 应用 `ROLLBACK` → 库用 **undo** 撤销未提交变更  

详见课堂中 redo / undo 的讨论；Java 侧只需正确划定事务边界。

---

## 7. 调用存储过程

Oracle 侧过程示意：

```sql
CREATE OR REPLACE PROCEDURE raise_sal(
  p_empno IN NUMBER,
  p_pct   IN NUMBER,
  p_new_sal OUT NUMBER
) AS
BEGIN
  UPDATE emp SET sal = sal * (1 + p_pct / 100) WHERE empno = p_empno
  RETURNING sal INTO p_new_sal;
  COMMIT;
END;
/
```

Java 调用：

```java
String sql = "{ call raise_sal(?, ?, ?) }";

try (Connection conn = DriverManager.getConnection(url, user, password);
     CallableStatement cs = conn.prepareCall(sql)) {

    cs.setLong(1, 7369);
    cs.setBigDecimal(2, new BigDecimal("10"));
    cs.registerOutParameter(3, Types.NUMERIC);
    cs.execute();

    BigDecimal newSal = cs.getBigDecimal(3);
}
```

注意：

- 过程内部若已 `COMMIT`，会与外层 Java 事务边界产生「自治/提前提交」效果，生产中要约定：**过程是否自己提交**  
- 更常见做法是过程内不提交，由 Spring 事务统一提交  

---

## 8. Spring Boot 接入

### 8.1 `application.yml` 示意

```yaml
spring:
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1
    username: app_user
    password: YourPassword1
    hikari:
      maximum-pool-size: 10
      connection-timeout: 30000
```

### 8.2 与 MySQL 配置对照

| 项 | MySQL（本仓库常见） | Oracle |
| --- | --- | --- |
| 驱动 | `com.mysql.cj.jdbc.Driver` | `oracle.jdbc.OracleDriver` |
| URL | `jdbc:mysql://host:3306/db` | `jdbc:oracle:thin:@//host:1521/service` |
| 依赖 | `mysql-connector-j` | `ojdbc11` / `ojdbc8` |
| 「库名」 | URL 里的 database | 更常体现为 **用户 / 服务名（PDB）** |

本仓库当前为 MySQL，见 `pom.xml` 中 `mysql-connector-j` 与 `spring-boot-starter-jdbc`。换 Oracle 时主要改：**依赖、URL、驱动、方言 / 分页插件**。

### 8.3 验证连通

启动后执行简单查询，或临时：

```java
@SpringBootApplication
public class Demo {
  public static void main(String[] args) {
    var ctx = SpringApplication.run(Demo.class, args);
    var ds = ctx.getBean(DataSource.class);
    try (var c = ds.getConnection()) {
      System.out.println(c.getMetaData().getDatabaseProductName());
    }
  }
}
```

---

## 9. MyBatis / MyBatis-Plus 要点

### 9.1 基本用法

Mapper 与 MySQL 类似，SQL 要写成 Oracle 方言：

```xml
<select id="findById" resultType="Book">
  SELECT id, title FROM book WHERE id = #{id}
</select>
```

### 9.2 分页

MySQL：

```sql
LIMIT #{offset}, #{size}
```

Oracle 12c+：

```sql
SELECT id, title FROM book
ORDER BY id
OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY
```

老版本常用 `ROWNUM` 三层嵌套。使用 PageHelper / MyBatis-Plus 时需配置 **Oracle 方言**，不能继续用 MySQL 分页拦截器默认值。

### 9.3 主键

Oracle 传统用 **序列 Sequence**：

```sql
CREATE SEQUENCE book_seq START WITH 1 INCREMENT BY 1;
```

```xml
<insert id="insert" >
  <selectKey keyProperty="id" resultType="long" order="BEFORE">
    SELECT book_seq.NEXTVAL FROM dual
  </selectKey>
  INSERT INTO book(id, title) VALUES (#{id}, #{title})
</insert>
```

12c+ 也可用 Identity 列；与 MySQL `AUTO_INCREMENT` 习惯不同，迁移时重点改这里。

---

## 10. 类型与 SQL 差异（相对 MySQL）

| 点 | MySQL | Oracle（Java 侧注意） |
| --- | --- | --- |
| 字符串 | `VARCHAR` | `VARCHAR2`；空字符串与 `NULL` 行为需当心 |
| 整数 / 小数 | `INT` / `DECIMAL` | 常用 `NUMBER`；Java 用 `BigDecimal` 更稳 |
| 日期时间 | `DATETIME` / `TIMESTAMP` | `DATE`（含时分秒）、`TIMESTAMP`；与 `java.time` 映射要对驱动版本 |
| 伪表 | 无 | 常写 `SELECT ... FROM dual` |
| 分页 | `LIMIT` | `FETCH` / `ROWNUM` |
| 自增 | `AUTO_INCREMENT` | Sequence / Identity |
| 布尔 | `TINYINT(1)` / `BOOLEAN` | 常用 `NUMBER(1)` 或 `CHAR(1)` |

`ResultSet` 取 `NUMBER` 时：

- 整型主键：`getLong` / `getInt`  
- 金额：优先 `getBigDecimal`  

---

## 11. 分页、主键与常见写法

### 11.1 12c+ 分页模板

```sql
SELECT * FROM (
  SELECT id, title FROM book WHERE title LIKE ? ORDER BY id
)
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
```

### 11.2 `dual` 取序列

```java
String sql = "SELECT book_seq.NEXTVAL FROM dual";
try (PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    if (rs.next()) {
        long id = rs.getLong(1);
    }
}
```

### 11.3 批量插入

使用 `addBatch` / `executeBatch`；注意单批大小与回滚段压力。大批量更常见 SQL\*Loader / 外部表，应用侧 batch 适可而止。

---

## 12. 连接池与生产注意

| 项 | 建议 |
| --- | --- |
| 连接池 | HikariCP（Spring Boot 默认） |
| 池大小 | 按 DB 会话上限与实例数估算，避免 `max` 过大打满 Oracle 进程 |
| 超时 | 设置 `connectionTimeout`、`idleTimeout` |
| 账号 | 专用业务用户 + 最小权限 |
| 密码 | 配置中心 / 环境变量，勿提交仓库 |
| 字符集 | 客户端与库字符集一致，避免中文乱码 |
| 游标 / 语句 | `try-with-resources` 关闭 `ResultSet` / `Statement` / `Connection` |

监听器、防火墙、PDB 服务名错误是课堂环境连不上的三大原因。

---

## 13. 与本仓库的对照

| 能力 | smart_bookstore 现状 | 若换成 Oracle |
| --- | --- | --- |
| 驱动 | `mysql-connector-j` | `ojdbc11` |
| ORM | MyBatis-Plus | 改方言、分页、主键策略 |
| 迁移 | Flyway MySQL 模块 | 换 Oracle 支持并改脚本语法 |
| SQL | MySQL 方言 | `VARCHAR2` / `FETCH` / Sequence 等 |

本文不要求改仓库代码；对照阅读有助于理解「换库时 Java 侧会动哪些点」。

---

## 14. 常见问题

**Q：报 `IO 错误` / `无法解析连接字符串`？**  
A：先核对 URL 是 Service Name 还是 SID 格式；再用 SQL\*Plus / PL/SQL Developer 确认主机、端口、服务名、账号。

**Q：`ORA-01017` 用户名密码错误？**  
A：检查用户是否锁定、大小写（密码加引号创建时大小写敏感）、是否连错 PDB。

**Q：`ORA-00942` 表或视图不存在？**  
A：多半连对了实例但 Schema 不对，或未授权。查 `SELECT USER FROM dual` 与 `user_tables`。

**Q：中文乱码？**  
A：统一库字符集、NLS_LANG / 连接属性、Java 源文件编码为 UTF-8。

**Q：事务不生效？**  
A：是否 `autoCommit=true`；存储过程内部是否提前 `COMMIT`；Spring 是否挂在同一线程数据源连接上。

**Q：和 MySQL 一样写 `LIMIT`？**  
A：不行。改 `FETCH` / `ROWNUM`，或配置分页插件的 Oracle 方言。

---

## 15. 能力自检

1. 写出 Thin 模式 Service Name 与 SID 两种 URL  
2. 默写一段 `PreparedStatement` 查询，并说明为何不用 Statement 拼接  
3. 说明 `COMMIT` / `ROLLBACK` 在 Java 与库（redo / undo）之间的关系  
4. 写出 `CallableStatement` 注册出参的关键步骤  
5. 列出从本仓库 MySQL 迁到 Oracle 时至少改动的 4 个点（依赖、URL、分页、主键）  
6. 解释为何业务禁止用 `SYS` 连接  

---

## 附录：最小可运行清单

1. 库已启动，监听 1521，已知服务名 / SID  
2. 业务用户可登录，有目标表权限  
3. 工程引入 `ojdbc`  
4. URL、用户名、密码正确  
5. 先跑通 `SELECT 1 FROM dual`（或 `SELECT USER FROM dual`），再写业务 SQL  

验证 SQL：

```sql
SELECT USER FROM dual;
SELECT * FROM user_tables;
```
