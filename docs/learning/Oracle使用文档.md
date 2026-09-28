# Oracle 使用文档

> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（通用数据库基础）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：[高并发](./高并发.md) · [MySQL 与 Oracle 保证 CP 面试文档](./MySQL与Oracle保证CP面试文档.md) · [Java 使用 Oracle 文档](./Java使用Oracle文档.md)  
> **说明**：本仓库运行时数据库以 MySQL 为主；本文面向 Oracle 入门与课堂/实验环境操作，便于对照关系库概念与管理工具。

本文覆盖 Oracle 核心架构、用户与表空间、常用客户端、基础 SQL、存储过程与触发器，以及和 MySQL 的对照。

---

## 目录

1. [学习目标](#1-学习目标)
2. [架构总览](#2-架构总览)
3. [物理文件](#3-物理文件)
4. [用户、Schema 与表空间](#4-用户schema-与表空间)
5. [内置账号](#5-内置账号)
6. [常用管理工具](#6-常用管理工具)
7. [连接与基础操作](#7-连接与基础操作)
8. [表与多表联查](#8-表与多表联查)
9. [存储过程](#9-存储过程)
10. [触发器](#10-触发器)
11. [与 MySQL 对照](#11-与-mysql-对照)
12. [PL-SQL Developer 使用提示](#12-plsql-developer-使用提示)
13. [常见问题](#13-常见问题)
14. [能力自检](#14-能力自检)

---

## 1. 学习目标

学完后应能：

1. 区分「数据库」「实例」「Oracle 服务器」三层含义
2. 说明数据文件、重做日志、控制文件各自干什么
3. 说清用户 / Schema / 表空间的关系，以及「同名表、不同用户、数据不同」的原因
4. 用 SQL\*Plus 或图形工具完成：建用户、授权、建表、查询、简单过程与触发器
5. 对照 MySQL：哪些能力两边都有，哪些是 Oracle 特有习惯

预计投入：1～2 天（含本地或实验机连库练习）。

---

## 2. 架构总览

### 2.1 三层关系

| 概念 | 是什么 | 关机后 |
| --- | --- | --- |
| **数据库（Database）** | 磁盘上的物理文件集合 | 文件还在 |
| **实例（Instance）** | 内存结构 + 后台进程 | 实例消失 |
| **Oracle 服务器** | 软件部件（如 SQL\*Plus）+ 实例 + 数据库 | — |

访问路径可以记成：

```text
应用程序 / 客户端
        ↓
   Oracle 实例（内存 + 后台进程）
        ↓
   数据库物理文件（数据 / 日志 / 控制文件）
```

要点：

- 实例为应用程序提供对库中数据的管理与维护能力
- 单机常见模型是「一个实例对应一个数据库」
- 跨实例不能直接 `JOIN`（需要 DB Link 等手段）

### 2.2 心智图（与课堂示意图一致）

```text
实例
 ├─ 1:N 用户（Schema）     例：LHB、LSZ
 └─ 1:N 表空间
      └─ 数据文件（.dbf / .ora 等）
           └─ 各用户的表对象落在其中

用户 N:1 表空间（多人可共用同一表空间）
表归属看「用户.表名」，不看落在哪个 dbf
```

---

## 3. 物理文件

Oracle 数据库在磁盘上的核心物理文件通常包括三类：

| 文件类型 | 作用 |
| --- | --- |
| **数据文件** | 存放表、索引等用户与系统数据 |
| **重做日志文件（Redo Log）** | 记录变更，用于崩溃恢复，保证事务可恢复 |
| **控制文件（Control File）** | 库的元数据目录：库名、数据文件/日志位置、SCN 等 |

一句话记忆：

- 数据在数据文件
- 改动痕迹在 redo
- 「怎么拼成一个库」看控制文件

入门先掌握这三类即可。完整环境还会有参数文件、归档日志、临时文件、密码文件等。

---

## 4. 用户、Schema 与表空间

### 4.1 概念区分

| 概念 | 管什么 |
| --- | --- |
| **用户 / Schema** | 对象归属（谁的表、视图、过程） |
| **表空间（Tablespace）** | 逻辑存储容器，映射到一个或多个数据文件 |
| **多表联查（JOIN）** | 按逻辑关系查询，不依赖表空间是否一对一 |

结论：

- 「一个用户默认对应一个表空间」**不会**阻止多表联查
- 同一用户下多张表可以 `JOIN`
- 不同用户下的表，有权限即可跨 Schema `JOIN`
- 表在不同表空间也可以 `JOIN`（表空间只影响物理存放）

### 4.2 同名表、不同用户

完整对象名是：

```text
用户名.表名
```

例如 `LHB.EMP` 与 `LSZ.EMP`：

- 不是「同一张表按用户各看一份」
- 而是**两张独立的表**（名字碰巧相同）
- 结构可以不同，数据彼此独立

若只有 `LHB.订单`，`LSZ` 在获得授权后访问的仍是 LHB 那份数据，不会自动拥有一份副本。

### 4.3 建用户与默认表空间（示意）

```sql
-- 需具备足够权限（如 SYSTEM）
CREATE USER app_user IDENTIFIED BY "YourPassword1"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA UNLIMITED ON users;

GRANT CONNECT, RESOURCE TO app_user;
-- 12c+ 常见还需显式授予建表等权限，按版本调整
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW,
      CREATE PROCEDURE, CREATE TRIGGER TO app_user;
```

建表时可指定表空间；不指定则落在该用户默认表空间：

```sql
CREATE TABLE book (
  id   NUMBER PRIMARY KEY,
  title VARCHAR2(200) NOT NULL
) TABLESPACE users;
```

---

## 5. 内置账号

| 用户 | 定位 | 使用建议 |
| --- | --- | --- |
| **SYS** | 超级用户，拥有数据字典，权限最高 | 启动/关闭、底层维护；日常少用 |
| **SYSTEM** | 默认管理员，具备 DBA 能力 | 建用户、日常管理；勿当业务账号 |
| **SCOTT** | 示范账号（`emp`、`dept` 等） | 练 SQL；新版本可能未解锁或未安装 |

补充：

- 业务系统应使用**自建用户**，最小权限原则
- `SYSDBA` 是特殊连接身份，不完全等同于「用户名叫 SYS」
- 查询当前语言环境示例：

```sql
SELECT USERENV('language') FROM dual;
-- 例：AMERICAN_AMERICA.ZHS16GBK
```

---

## 6. 常用管理工具

| 工具 | 类型 | 典型用途 |
| --- | --- | --- |
| **SQL\*Plus** | 命令行 | 脚本、运维、装库后基础操作 |
| **SQL Developer** | 官方图形工具 | 写 SQL、浏览对象、调试 |
| **OEM（Enterprise Manager）** | 网页/集中管理 | 监控、告警、库管 |
| **PL/SQL Developer** | 第三方客户端 | 课堂/个人常用，功能接近 SQL Developer |

---

## 7. 连接与基础操作

### 7.1 SQL\*Plus 连接示意

```text
sqlplus system/密码@主机:1521/服务名
sqlplus scott/tiger@ORCL
sqlplus sys/密码@ORCL AS SYSDBA
```

常见服务名 / SID 以本机安装为准（如 `ORCL`、`ORCLPDB`）。

### 7.2 常用元数据查询

```sql
-- 当前用户
SELECT USER FROM dual;

-- 当前用户下的表
SELECT table_name FROM user_tables ORDER BY table_name;

-- 表结构
DESC emp;

-- 有权限看到的对象（含其他用户）
SELECT owner, object_name, object_type
FROM all_objects
WHERE object_type = 'TABLE'
ORDER BY owner, object_name;
```

### 7.3 事务习惯

```sql
INSERT INTO emp (empno, ename) VALUES (9999, 'TEST');
COMMIT;    -- 提交
-- 或
ROLLBACK;  -- 回滚
```

Oracle 默认很多客户端不会自动提交，改数据后记得 `COMMIT`（以工具设置为准）。

---

## 8. 表与多表联查

### 8.1 同 Schema 联查

```sql
SELECT e.ename, d.dname
FROM emp e
JOIN dept d ON e.deptno = d.deptno;
```

### 8.2 跨 Schema 联查（需授权）

```sql
-- 由属主授权
GRANT SELECT ON lhb.orders TO lsz;

-- LSZ 侧查询
SELECT o.*, i.*
FROM lhb.orders o
JOIN lsz.order_items i ON o.id = i.order_id;
```

联查是否可行，取决于：

1. 是否在**同一数据库实例**内
2. 调用方是否有对象权限  

与「是否同一表空间」「是否一对一用户表空间」无关。

---

## 9. 存储过程

### 9.1 是什么

预编译并存放在库中的 PL/SQL 程序块，由应用或人**主动调用**。

典型用途：

- 下单：扣库存、写订单、写流水
- 批量结算、对账、清洗
- 复杂校验后集中写库

### 9.2 示例

```sql
CREATE OR REPLACE PROCEDURE raise_sal(
  p_empno IN NUMBER,
  p_pct   IN NUMBER
) AS
BEGIN
  UPDATE emp
     SET sal = sal * (1 + p_pct / 100)
   WHERE empno = p_empno;
  COMMIT;
END;
/

-- 调用
BEGIN
  raise_sal(7369, 10);
END;
/
```

特点：可带入参 / 出参；可被 Java、其他过程、定时任务调用。  
代价：业务逻辑进库后，版本管理、调试、跨库迁移往往更重。

---

## 10. 触发器

### 10.1 是什么

挂在表（或视图等）上的程序，在 `INSERT` / `UPDATE` / `DELETE` 等事件发生时**自动执行**，无需显式 `CALL`。

典型用途：

| 场景 | 例子 |
| --- | --- |
| 审计 | 改工资时写入日志表 |
| 派生数据 | 插明细后更新订单总额 |
| 约束补充 | 禁止删除特定状态行 |

### 10.2 示例

```sql
CREATE OR REPLACE TRIGGER trg_emp_sal
AFTER UPDATE OF sal ON emp
FOR EACH ROW
BEGIN
  INSERT INTO emp_sal_log(empno, old_sal, new_sal, changed_at)
  VALUES (:OLD.empno, :OLD.sal, :NEW.sal, SYSDATE);
END;
/
```

建议：触发器宜少、宜短；复杂业务优先放在存储过程或应用层，避免「只写了 `UPDATE`，背后跑了一大串隐式逻辑」导致难排查。

### 10.3 过程 vs 触发器

| | 存储过程 | 触发器 |
| --- | --- | --- |
| 谁启动 | 主动调用 | 数据变更自动触发 |
| 典型用途 | 封装业务流程 | 监控 / 联动增删改 |
| 可见性 | 调用方知道在调过程 | 调用方可能无感知 |

---

## 11. 与 MySQL 对照

| 能力 | Oracle | MySQL |
| --- | --- | --- |
| 存储过程 / 函数 | PL/SQL，生态厚 | 有，语法与能力相对弱 |
| 触发器 | 成熟 | 有，限制更多 |
| Package | 有 | 无原生 Package |
| Schema | 用户 ≈ Schema | 库（Database）是主要命名空间 |
| 自增主键 | 序列 Sequence / Identity | `AUTO_INCREMENT` |
| 分页 | `FETCH FIRST` / `ROWNUM` / `OFFSET` | `LIMIT` |
| 字符串类型 | `VARCHAR2` 等 | `VARCHAR` 等 |
| 双表工具习惯 | SQL\*Plus、SQL Developer | mysql 客户端、Workbench |

两边都有「过程 + 触发器」：  
要主动执行一段业务 → 过程；一改表就自动做事 → 触发器。  
Java 项目里更常见的是逻辑放在应用层，库内过程 / 触发器用于审计、补数或遗留系统。

---

## 12. PL/SQL Developer 使用提示

### 12.1 放大编辑器字体

1. **Tools（工具）** → **Preferences（首选项）**
2. 打开 **Editor**
3. 点击预览旁的 **Select...**
4. 调大字体 Size（如 12 → 16）后确定并 Apply

右侧 `Space` / `Tab` / `Line break` 的数字是「特殊字符显示符号」，不是字号。

若整体界面仍偏小：

- **Preferences → User Interface → Fonts** 调菜单、对象树、结果网格字体
- 或 Windows：**设置 → 系统 → 显示 → 缩放**（125% / 150%），再重启客户端
- 临时放大全屏：**Win + `+`**（放大镜），**Win + Esc** 退出

### 12.2 查看语言环境

在 SQL Window 执行：

```sql
SELECT USERENV('language') FROM dual;
```

---

## 13. 常见问题

**Q：一个用户一个表空间，就不能多表联查了？**  
A：不能。表空间是存储概念；联查看的是同实例内的逻辑表与权限。

**Q：两个用户都有一张叫 `EMP` 的表，是同一张吗？**  
A：不是。是 `USER1.EMP` 与 `USER2.EMP` 两张独立对象，数据默认互不影响。

**Q：SYS 和 SYSTEM 有什么区别？**  
A：SYS 权限更高、拥有数据字典，偏底层；SYSTEM 是常用 DBA 管理账号。业务都不要用它们登录。

**Q：改完数据查不到？**  
A：检查是否未 `COMMIT`；以及是否连错用户 / 查错 Schema。

**Q：MySQL 有没有存储过程和触发器？**  
A：都有，角色类似；Oracle 的 PL/SQL 与工具链通常更完整。

---

## 14. 能力自检

1. 用自己的话区分：数据库、实例、Oracle 服务器
2. 说出三类核心物理文件的作用
3. 解释：用户 N:1 表空间时，为什么仍能多表 / 跨用户联查
4. 写出：给用户授权后，跨 Schema `JOIN` 的示意 SQL
5. 对比存储过程与触发器的启动方式与适用场景
6. 说出 SYS / SYSTEM / SCOTT 各自适合干什么

---

## 附录：课堂小结对照

与常见课件表述对齐：

- 物理文件包括：数据文件、重做日志文件、控制文件
- 实例是后台进程和内存结构的集合
- Oracle 服务器一般指：软件部件 + 实例 + 数据库
- SYS 为超级用户；SYSTEM 为默认管理员（DBA）；SCOTT 为示范账号
- 管理工具主要包括：SQL\*Plus、SQL Developer、Oracle Enterprise Manager
