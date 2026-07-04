# JWT / Refresh Token 完整链路
## 一、为什么需要双 Token

纯 Session（服务端存会话、Cookie 带 SessionId）在前后端分离场景下会遇到：

- 跨域 Cookie 配置复杂
- 移动端 / 第三方客户端不便使用 Cookie
- 水平扩展时需要 Session 共享（Redis 等）

纯 JWT（只发一个长期 Token）又会有：

- Token 一旦泄露，在过期前无法单方面作废
- 无法在服务端主动踢人、撤销登录

**双 Token（Access Token + Refresh Token）** 是常见折中方案：

| Token | 生命周期 | 存储位置 | 用途 |
|-------|----------|----------|------|
| **Access Token** | 短（15min～2h） | 内存 / 短期存储 | 每次请求携带，访问受保护 API |
| **Refresh Token** | 长（7～30 天） | 安全存储（HttpOnly Cookie 或加密本地存储） | 仅用于换取新的 Access Token |

```text
         ┌─────────────┐
         │   客户端     │
         └──────┬──────┘
                │ Authorization: Bearer <accessToken>
                ▼
         ┌─────────────┐     access 过期      ┌─────────────┐
         │  业务 API    │ ◄────────────────── │  /refresh   │
         └─────────────┘   携带 refreshToken   └─────────────┘
                │                                    │
                │ 200 + 新 accessToken               │ 校验 session
                ▼                                    ▼
         ┌─────────────┐                      ┌─────────────┐
         │  继续访问    │                      │ auth_session │
         └─────────────┘                      └─────────────┘
```

---

## 二、标准完整链路（生产级）

一条「完整」的 JWT + Refresh Token 链路通常包含 **6 个环节**：

```text
登录签发 → 携带访问 → 网关/拦截器校验 → Access 过期刷新 → 登出撤销 → 安全策略
```

### 2.1 登录签发（Login）

```mermaid
sequenceDiagram
    participant C as 客户端
    participant AC as AuthController
    participant AS as AuthService
    participant H as LoginHandler
    participant DB as auth_session

    C->>AC: POST /api/auth/login
    AC->>AS: login(req, ip)
    AS->>H: handler.handle(req)
    H-->>AS: AuthUser
    AS->>AS: 生成 Access JWT + Refresh Token
    AS->>DB: 写入 session（jti + hash + 过期时间）
    AS-->>AC: LoginResponse
    AC-->>C: accessToken + refreshToken + expireIn
```

**Access Token（JWT）建议 Payload：**

```json
{
  "sub": "1",
  "username": "admin",
  "iat": 1710000000,
  "exp": 1710007200,
  "jti": "access-uuid",
  "typ": "access"
}
```

**Refresh Token 建议：**

- 格式：`{jti}:{randomSecret}`（本项目已采用）
- 数据库 **只存 jti + SHA-256(refreshToken)**，不存明文
- `rememberMe=true` 时延长 Refresh 有效期（7 天 → 30 天）

---

### 2.2 携带 Access Token 访问受保护接口

```http
GET /api/user/profile HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**服务端校验步骤：**

1. 从 Header 提取 `Bearer` Token
2. 验证 JWT 签名（HMAC / RSA）
3. 检查 `exp` 是否过期
4. 检查 `typ` 是否为 `access`（防止用 refresh 当 access 用）
5. 可选：查黑名单 / 用户状态（禁用则拒绝）
6. 解析 `sub` 注入当前用户上下文

```mermaid
sequenceDiagram
    participant C as 客户端
    participant F as AuthInterceptor
    participant J as JwtService
    participant API as 业务 Controller

    C->>F: 请求 + Authorization
    F->>J: parseAndValidate(token)
    alt 合法且未过期
        J-->>F: UserContext
        F->>API: 放行
        API-->>C: 200
    else 过期
        F-->>C: 401 TOKEN_EXPIRED
    else 非法
        F-->>C: 401 INVALID_TOKEN
    end
```

---

### 2.3 Access 过期 → 刷新（Refresh）

```mermaid
sequenceDiagram
    participant C as 客户端
    participant AC as AuthController
    participant AS as AuthService
    participant DB as auth_session

    C->>AC: POST /api/auth/refresh<br/>Body: jti:secret
    AC->>AS: refresh(refreshToken)
    AS->>AS: 解析 jti
    AS->>DB: findByRefreshTokenJti(jti)
    alt session 不存在 / 已撤销 / 已过期 / hash 不匹配
        AS-->>AC: null
        AC-->>C: 2001 invalid or expired refresh token
    else 校验通过
        AS->>AS: 签发新 Access JWT
        AS-->>AC: LoginResponse
        AC-->>C: 新 accessToken + refreshToken
    end
```

**Refresh 策略有两种（需明确选型）：**

| 策略 | 说明 | 安全性 | 本项目现状 |
|------|------|--------|------------|
| **Refresh 复用** | 刷新时 Refresh Token 不变 | 中 | ✅ 当前实现 |
| **Refresh 轮换（Rotation）** | 每次刷新生成新 Refresh，旧 jti 作废 | 高（防重放） | ❌ 待实现 |

生产环境推荐 **Refresh Token Rotation**：旧 Token 一旦被重复使用，可判定为泄露并撤销整条 session 链。

---

### 2.4 登出与撤销（Logout / Revoke）

完整链路必须支持「主动失效」：

```mermaid
sequenceDiagram
    participant C as 客户端
    participant AC as AuthController
    participant AS as AuthService
    participant DB as auth_session

    C->>AC: POST /api/auth/logout<br/>Authorization: Bearer access
    AC->>AS: logout(accessToken / sessionId)
    AS->>DB: UPDATE auth_session SET revoked_at = NOW()
    AS-->>C: 成功
    Note over C: 客户端清除本地 Token
```

**可选增强：**

- 单设备登出：撤销当前 session
- 全设备登出：撤销该用户所有 session
- Access JWT 短期有效 + 黑名单（Redis 存已撤销的 jti，TTL = 剩余过期时间）

---

### 2.5 前端完整协作链路

```mermaid
flowchart TD
    A[用户登录] --> B[保存 access + refresh]
    B --> C[业务请求带 Authorization]
    C --> D{响应状态}
    D -->|200| E[正常处理]
    D -->|401 且 TOKEN_EXPIRED| F{是否正在刷新}
    F -->|否| G[调用 /refresh]
    F -->|是| H[请求进入等待队列]
    G --> I{刷新成功?}
    I -->|是| J[更新 Token 并重试原请求]
    I -->|否| K[跳转登录页]
    H --> J
    D -->|401 其他| K
```

**前端 Token 存储建议：**

| 存储方式 | Access | Refresh | 说明 |
|----------|--------|---------|------|
| **推荐** | 内存变量 | HttpOnly Cookie（后端 Set-Cookie） | 防 XSS 窃取 Refresh |
| **常见** | sessionStorage | localStorage | 实现简单，XSS 风险更高 |
| **避免** | localStorage 长期存 Refresh | — | 泄露窗口过大 |

**前端刷新防并发（伪代码）：**

```javascript
let isRefreshing = false;
let pendingQueue = [];

async function request(url, options) {
  options.headers = { ...options.headers, Authorization: `Bearer ${accessToken}` };
  let resp = await fetch(url, options);
  if (resp.status !== 401) return resp;

  if (!isRefreshing) {
    isRefreshing = true;
    try {
      const newTokens = await refresh(refreshToken);
      accessToken = newTokens.accessToken;
      refreshToken = newTokens.refreshToken;
      pendingQueue.forEach(cb => cb(accessToken));
      pendingQueue = [];
    } catch {
      redirectToLogin();
      return resp;
    } finally {
      isRefreshing = false;
    }
  } else {
    await new Promise(resolve => pendingQueue.push(resolve));
  }

  options.headers.Authorization = `Bearer ${accessToken}`;
  return fetch(url, options);
}
```

---

## 三、本项目当前实现

### 3.1 已实现部分

| 环节 | 状态 | 说明 |
|------|------|------|
| 登录签发双 Token | ✅ 部分 | 登录成功后返回 `accessToken` + `refreshToken` |
| Session 持久化 | ✅ | 写入 `auth_session` 表 |
| Refresh 校验 | ✅ | jti 查找 + hash 比对 + 过期 + 撤销检查 |
| Refresh 接口 | ✅ | `POST /api/auth/refresh` |
| rememberMe 延长 Refresh | ✅ | 7 天 / 30 天 |

**核心代码：登录创建 Session**

```59:81:src/main/java/com/zx/auth/service/AuthService.java
    private LoginResponse createSessionAndResponse(AuthUser user, boolean rememberMe) {
        String access = "access:" + UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String refresh = jti + ":" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExp = now.plusHours(2);
        LocalDateTime refreshExp = now.plusDays(rememberMe?30:7);

        AuthSession s = new AuthSession();
        s.setUser(user);
        s.setRefreshTokenJti(jti);
        s.setRefreshTokenHash(sha256Hex(refresh));
        s.setAccessExpiresAt(accessExp);
        s.setRefreshExpiresAt(refreshExp);
        s.setRememberMe(rememberMe);
        sessionRepo.save(s);

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refresh);
        resp.setExpireIn(7200);
        resp.setUserInfo(new LoginResponse.UserInfo(user.getId(), user.getUsername()));
        return resp;
    }
```

**核心代码：Refresh 校验**

```84:101:src/main/java/com/zx/auth/service/AuthService.java
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null) return null;
        String[] parts = refreshToken.split(":", 2);
        if (parts.length != 2) return null;
        String jti = parts[0];
        Optional<AuthSession> so = sessionRepo.findByRefreshTokenJti(jti);
        if (so.isEmpty()) return null;
        AuthSession s = so.get();
        if (s.getRevokedAt() != null) return null;
        if (s.getRefreshExpiresAt().isBefore(LocalDateTime.now())) return null;
        if (!s.getRefreshTokenHash().equals(sha256Hex(refreshToken))) return null;
        String access = "access:" + UUID.randomUUID();
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refreshToken);
        resp.setExpireIn(7200);
        resp.setUserInfo(new LoginResponse.UserInfo(s.getUser().getId(), s.getUser().getUsername()));
        return resp;
    }
```

**数据库表结构（`auth_session`）：**

```60:77:src/main/resources/db/schema.sql
CREATE TABLE IF NOT EXISTS auth_session (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  refresh_token_jti CHAR(36) NOT NULL COMMENT 'refresh token唯一ID（建议UUID）',
  refresh_token_hash VARCHAR(255) NOT NULL COMMENT '建议存哈希',
  access_expires_at DATETIME NOT NULL,
  refresh_expires_at DATETIME NOT NULL,
  remember_me       TINYINT NOT NULL DEFAULT 0,
  device_info       VARCHAR(255) NULL,
  login_ip          VARCHAR(45) NULL,
  revoked_at        DATETIME NULL,
  ...
);
```

### 3.2 当前与标准 JWT 链路的差距

| 环节 | 现状 | 目标（完整链路） |
|------|------|------------------|
| Access Token 格式 | `access:UUID` 占位符 | 标准 JWT（HS256 / RS256 签名） |
| Access 校验 | ❌ 无拦截器 | `OncePerRequestFilter` 或 `HandlerInterceptor` |
| 受保护 API | ❌ 暂无 | 除白名单外均需 Bearer Token |
| 登出接口 | ❌ 暂无 | `POST /api/auth/logout` + `revoked_at` |
| Refresh 轮换 | ❌ 复用旧 Refresh | 可选：每次刷新生成新 Refresh |
| JWT 依赖 | ❌ pom 未引入 | `jjwt` 或 `nimbus-jose-jwt` |
| Token 黑名单 | ❌ 无 | Redis 存已撤销 access jti（可选） |
| 刷新后更新 session | ❌ 未更新 `access_expires_at` | 刷新时同步更新 session 记录 |

> **说明：** 当前实现已具备 Refresh Token **服务端校验闭环**（jti + hash + 过期 + 撤销字段），适合联调与理解双 Token 流程；Access Token 仍为演示占位符，尚未接入 JWT 验签与鉴权拦截。

---

## 四、演进为完整 JWT 链路的实现清单

按推荐顺序分阶段落地：

### 阶段 1：JWT 基础设施

1. 引入依赖（示例）：

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

2. 新增 `JwtService`：

```java
public interface JwtService {
    String createAccessToken(Long userId, String username);
    Claims parseAccessToken(String token);
    boolean isAccessToken(String token);
}
```

3. `application.yaml` 配置：

```yaml
auth:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-change-in-production}
    access-expire-seconds: 7200      # 2h
    refresh-expire-days: 7
    refresh-expire-days-remember: 30
    issuer: Factory_Test_Demo
```

4. 替换 `createSessionAndResponse` 中的 `"access:" + UUID` 为 `jwtService.createAccessToken(...)`。

---

### 阶段 2：鉴权拦截

1. 新增 `AuthInterceptor` 或 `JwtAuthenticationFilter`
2. 白名单路径：

```text
/api/auth/login
/api/auth/code/send
/api/auth/refresh
/actuator/health
```

3. 校验通过后，将 `userId` 写入 `RequestContext` 或 Spring Security `SecurityContext`

4. 统一 401 响应体：

```json
{
  "code": 2002,
  "message": "token expired",
  "data": null
}
```

---

### 阶段 3：登出与撤销

1. 新增 `POST /api/auth/logout`
2. 根据当前 Access Token 中的 session 标识或用户 ID 设置 `revoked_at`
3. 可选：`POST /api/auth/logout-all` 撤销用户全部 session

---

### 阶段 4：Refresh 增强（可选）

1. **Refresh Token Rotation**：刷新时生成新 jti + 新 refresh，旧 session 标记撤销
2. 刷新成功后更新 `access_expires_at`
3. 记录 `device_info`、`login_ip`（表字段已预留）

---

## 五、接口规格汇总

### 5.1 登录

- **URL**：`POST /api/auth/login`
- **响应字段**：`accessToken`、`refreshToken`、`expireIn`、`userInfo`

### 5.2 刷新

- **URL**：`POST /api/auth/refresh`
- **Content-Type**：`text/plain`
- **Body**：Refresh Token 字符串，如 `550e8400-e29b-41d4-a716-446655440000:随机段`

**成功响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000:随机段",
    "expireIn": 7200,
    "userInfo": { "id": 1, "username": "admin" }
  }
}
```

**失败响应：**

```json
{
  "code": 2001,
  "message": "invalid or expired refresh token",
  "data": null
}
```

### 5.3 登出（待实现）

- **URL**：`POST /api/auth/logout`
- **Header**：`Authorization: Bearer <accessToken>`

### 5.4 受保护业务接口（待实现拦截后）

- **Header**：`Authorization: Bearer <accessToken>`

---

## 六、安全要点 checklist

| 项 | 要求 |
|----|------|
| Refresh 存库 | 只存 **哈希**，不存明文 |
| Access 有效期 | 短（≤ 2h），降低泄露影响 |
| HTTPS | 生产环境必须启用 |
| JWT Secret | 环境变量注入，禁止硬编码进仓库 |
| 撤销能力 | `revoked_at` + 登出接口 |
| 防暴力刷新 | 对 `/refresh` 做 IP / 用户限流 |
| 防 XSS | Refresh 优先 HttpOnly Cookie |
| 防 CSRF | Cookie 方案需 SameSite + CSRF Token |
| 用户禁用 | 登录 / 刷新 / 鉴权时检查 `auth_user.status` |

---

## 七、时序总览（端到端）

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant FE as 前端
    participant BE as 后端
    participant DB as MySQL

    U->>FE: 输入账号/验证码
    FE->>BE: POST /login
    BE->>DB: 校验用户 + 写 auth_session
    BE-->>FE: access + refresh
    FE->>FE: 保存 Token

    loop 业务操作
        FE->>BE: API + Bearer access
        alt access 有效
            BE-->>FE: 200
        else access 过期
            BE-->>FE: 401
            FE->>BE: POST /refresh
            BE->>DB: 校验 refresh hash
            BE-->>FE: 新 access
            FE->>BE: 重试原请求
        end
    end

    U->>FE: 点击退出
    FE->>BE: POST /logout
    BE->>DB: revoked_at = now()
    FE->>FE: 清除 Token
```

---

## 八、面试可讲要点

1. **为什么双 Token？** Access 短期无状态校验高效；Refresh 长期有状态可撤销。
2. **Refresh 为什么存 hash？** 数据库泄露时攻击者无法直接使用 Refresh。
3. **jti 的作用？** Refresh 唯一标识，便于查找 session 和撤销。
4. **JWT 无状态 vs Session 有状态？** Access 用 JWT 减少查库；Refresh 落库保证可控。
5. **Refresh Rotation 是什么？** 每次刷新生成新 Refresh，旧 Token 作废，防重放。
6. **前端 401 怎么处理？** 单例刷新 + 请求队列，避免并发多次 refresh。

---

## 九、相关文档

- [接口文档](./接口文档.md) — 登录、刷新接口请求/响应细节  
- [前后端联调文档](./前后端联调文档.md) — 前端环境配置与返回字段  
- [工厂模式实战博客](./工厂模式实战博客.md) — 多登录方式与认证架构  
- [大二项目规划指南](./大二项目规划指南.md) — JWT 作为项目深化方向  

---

## 十、一句话总结

**完整链路 = JWT Access（短、无状态验签）+ Refresh（长、落库可撤销）+ 鉴权拦截 + 过期刷新 + 登出失效 + 前端防并发刷新。**

本项目已完成 **Refresh 服务端校验闭环** 和 **Session 持久化**；下一步重点是 **接入真实 JWT**、**鉴权拦截器** 和 **登出接口**，即可形成可写进简历的完整认证链路。
