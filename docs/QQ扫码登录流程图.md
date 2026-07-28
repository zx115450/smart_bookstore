# QQ 官方 JS SDK（PC 扫码登录）流程图


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联**：`com.zx.auth.oauth` — OAuth 时序  
> **索引**：[学习文档中心](./README.md)

> 适用场景：PC 网页端，用户使用**手机 QQ 扫码**完成第三方登录  
> 官方平台：[QQ 互联](https://connect.qq.com/)  
> 协议基础：**OAuth 2.0 授权码模式**（扫码只是前端交互形态，本质仍是 OAuth）  
> 关联文档：[登录流程学习文档](./登录流程学习文档.md) · [接口文档](./接口文档.md) · [项目流程图](./项目流程图.md)

---

## 一、写在前面

PC 端「QQ 扫码登录」并不是一套独立于 OAuth 之外的协议。QQ 互联提供的 **JS SDK** 在页面上展示二维码，用户用手机 QQ 扫描确认后，浏览器仍会走 **授权码（code）→ 换 Token → 取 openid** 的标准流程。

本文说明：

1. QQ 官方 JS SDK 扫码登录的**完整链路**
2. 前端、QQ 服务器、自己后端各自做什么
3. 与 `smart_bookstore` 双 Token 架构如何衔接（**设计参考**，当前仓库尚未接入 QQ OAuth）

---

## 二、前置准备（QQ 互联控制台）

```mermaid
flowchart LR
    A[注册 QQ 互联开发者] --> B[创建网站应用]
    B --> C[获取 App ID 和 App Key]
    C --> D[配置回调地址 redirect_uri]
    D --> E[申请 scope 权限]
    E --> F[前端引入 JS SDK]
    F --> G[后端实现 callback]
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| **App ID** | 应用标识，前端 SDK 必填 | `101234567` |
| **App Key** | 应用密钥，**仅后端**使用 | 环境变量，不进 Git |
| **redirect_uri** | 授权成功后的回调地址 | `https://your-domain.com/api/auth/oauth/qq/callback` |
| **scope** | 授权范围 | `get_user_info` 等 |
| **网站域名** | 与前端页面域名一致 | 控制台需备案/校验 |

> **注意：** 本地开发时 `redirect_uri` 需与 QQ 控制台配置**完全一致**（含协议、端口、路径）。`localhost` 是否支持以 QQ 互联当前规则为准。

---

## 三、角色与职责

```mermaid
flowchart TB
    subgraph Browser["PC 浏览器"]
        Page[登录页]
        SDK["QQ JS SDK 二维码组件"]
    end

    subgraph QQ["QQ 互联"]
        Auth["授权服务器 graph qq com"]
        API[Open API]
    end

    subgraph Backend["你的 Spring Boot 后端"]
        AuthCtrl[OAuth Controller]
        QqSvc[QqOAuthService]
        AuthSvc[AuthService]
        Redis[(Redis)]
        MySQL[(MySQL)]
    end

    subgraph Mobile["手机 QQ App"]
        QQApp[扫码确认]
    end

    Page --> SDK
    SDK --> Auth
    QQApp --> Auth
    Auth --> AuthCtrl
    AuthCtrl --> QqSvc
    QqSvc --> API
    AuthCtrl --> AuthSvc
    AuthSvc --> Redis
    AuthSvc --> MySQL
```

| 角色 | 职责 |
|------|------|
| **PC 浏览器 + JS SDK** | 展示二维码、发起 OAuth、接收 redirect |
| **手机 QQ** | 用户扫码、确认授权 |
| **QQ 互联** | 签发 code、换 access_token、提供 openid |
| **你的后端** | 验 state、换 openid、查/建用户、发双 Token |
| **Redis** | 存 OAuth state（防 CSRF）、Session 缓存等 |
| **MySQL** | 存用户、openid 绑定、Session |

---

## 四、端到端总览（扫码登录）

```mermaid
sequenceDiagram
    participant U as 用户
    participant PC as PC 浏览器
    participant SDK as QQ JS SDK
    participant QQ as QQ 互联
    participant App as QQ 手机 App
    participant BE as 你的后端
    participant R as Redis
    participant DB as MySQL

    U->>PC: 打开登录页
    PC->>BE: GET oauth qq authorize 可选取 state
    BE->>R: SET auth:oauth:state key
    BE-->>PC: 返回 state 或 authorize 参数

    PC->>SDK: 初始化 QC.Login
    SDK->>QQ: 请求授权页与二维码
    QQ-->>SDK: 展示二维码
    SDK-->>PC: 页面显示二维码

    U->>App: 手机 QQ 扫一扫
    App->>QQ: 用户确认授权

    QQ->>PC: 302 redirect_uri 带 code 和 state
    PC->>BE: GET oauth qq callback

    BE->>R: 校验并删除 state
    BE->>QQ: code 换 access_token
    BE->>QQ: access_token 换 openid
    BE->>DB: openid 查或建用户与 identity
    BE->>DB: INSERT auth_session
    BE->>R: cache Session
    BE-->>PC: 302 前端 + Token 或 JSON

    PC->>PC: 保存 accessToken + refreshToken
    U->>PC: 进入已登录状态
```

---

## 五、前端：QQ 官方 JS SDK 流程

### 5.1 页面集成步骤

```mermaid
flowchart TD
    A[登录页加载] --> B[引入 connect.qq.com JS SDK]
    B --> C[向后端申请 state]
    C --> D[QC.Login 初始化]
    D --> E[SDK 渲染二维码区域]
    E --> F{用户扫码}
    F -->|成功| G[浏览器跳转 redirect_uri 带 code]
    F -->|取消或过期| H[SDK 提示失败 可刷新二维码]
    G --> I[进入后端 callback 或前端回调页]
```

### 5.2 前端伪代码（示意）

```html
<div id="qq-login-container"></div>
<script src="https://connect.qq.com/qc_jssdk.js"></script>
<script>
  // 1. 先从后端拿 state（推荐，不要前端自己造）
  const { state } = await fetch('/api/auth/oauth/qq/state').then(r => r.json());

  // 2. 初始化 QQ 登录（具体 API 以 QQ 互联最新文档为准）
  QC.Login({
    btnId: 'qq-login-container',
    appId: '你的 App ID',
    redirectURI: 'https://your-domain.com/api/auth/oauth/qq/callback',
    scope: 'get_user_info',
    state: state,
    // PC 端 SDK 会在容器内展示二维码或登录按钮
  });
</script>
```

### 5.3 前端两种 callback 处理方式

```mermaid
flowchart LR
    subgraph 方式A["方式 A 后端 callback 重定向 推荐"]
        A1[QQ 回调后端] --> A2[后端换 openid 发 Token]
        A2 --> A3[302 到前端 oauth success 页]
        A3 --> A4[前端存 Token 进首页]
    end

    subgraph 方式B["方式 B callback 页在前端域名"]
        B1[QQ 回调前端静态页] --> B2[前端拿 code POST 给后端]
        B2 --> B3[后端返回 JSON Token]
    end
```

| 方式 | 优点 | 注意 |
|------|------|------|
| **A：后端 callback** | App Key 不暴露、state 校验集中 | redirect_uri 指向后端 |
| **B：前端 callback** | 前后端分离部署灵活 | 必须用 PKCE 或严格 state；code 尽快换 token |

`smart_bookstore` 推荐 **方式 A**，与现有 Spring Security + 双 Token 更一致。

---

## 六、后端：OAuth Callback 流程

### 6.1 建议接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth/qq/state` | 生成 state 写入 Redis，返回给前端 |
| GET | `/api/auth/oauth/qq/authorize` | （可选）直接 302 到 QQ 授权页 |
| GET | `/api/auth/oauth/qq/callback` | QQ 回调，换 openid，登录，重定向前端 |

### 6.2 Callback 详细流程

```mermaid
flowchart TD
    Start[GET callback 带 code 和 state] --> V1{state 有效}
    V1 -->|否| E1[400 非法请求]
    V1 -->|是| V2[DELETE Redis state Key]

    V2 --> T1["POST token 接口 换 access_token"]
    T1 --> T2{换 token 成功}
    T2 -->|否| E2[401 code 无效或过期]
    T2 -->|是| T3["GET me 接口 取 openid"]

    T3 --> U1{openid 已绑定用户}
    U1 -->|是| U2[加载 AuthUser]
    U1 -->|否| U3[创建 AuthUser + auth_user_identity]
    U3 --> U2

    U2 --> S1[ensureDefaultUserRole]
    S1 --> S2[createRotatedSession 双 Token]
    S2 --> S3[写 auth_login_audit]
    S3 --> R1[302 重定向到前端携带 Token 或 Set-Cookie]
```

### 6.3 调用的 QQ Open API

| 步骤 | URL | 说明 |
|------|-----|------|
| 换 Token | `https://graph.qq.com/oauth2.0/token` | grant_type=authorization_code |
| 取 openid | `https://graph.qq.com/oauth2.0/me` | 返回 openid（JSONP 或 JSON） |
| 用户信息（可选） | `https://graph.qq.com/user/get_user_info` | 昵称、头像 |

> **unionid：** 若同一主体下有多个 QQ 应用，建议用 unionid 作为 `identity_value`，避免同一用户在不同 App 下 openid 不同。

---

## 七、与 smart_bookstore 双 Token 衔接

```mermaid
flowchart LR
    QQ[QQ OAuth 完成] --> User[得到 AuthUser]
    User --> AS[AuthService.createSessionAndResponse]
    AS --> MySQL[(auth_session)]
    AS --> Redis[(Session cache)]
    AS --> JWT[Access JWT + Refresh Token]
    JWT --> FE[前端存储]
    FE --> API["GET user me 等受保护 API"]
    API --> JF[JwtAuthenticationFilter]
```

QQ 扫码登录**不需要**改动现有核心：

| 模块 | 是否改动 |
|------|----------|
| `JwtService` / Access JWT | 不变 |
| `createRotatedSession` / Rotation | 不变 |
| `JwtAuthenticationFilter` | 不变 |
| `AuthRedisService` 黑名单 | 不变 |
| `AuthSessionCacheService` | 不变 |
| **新增** | `QqOAuthController`、`QqOAuthService`、identity 类型 `qq_openid` |

登录成功后与密码登录、验证码登录**共用同一套 Token 与 Session 机制**。

---

## 八、Redis 在 QQ 扫码流程中的作用

```mermaid
flowchart TB
    subgraph OAuth["OAuth 阶段"]
        R1["auth:oauth:state key"]
    end

    subgraph Login["登录成功后（与现有流程相同）"]
        R2["auth:session:id sessionId"]
        R3["auth:session:jti jti"]
    end

    OAuth --> Login
```

| Key | 时机 | TTL | 作用 |
|-----|------|-----|------|
| `auth:oauth:state:{state}` | authorize 前写入 | 5～10 分钟 | 防 CSRF，callback 校验后删除 |
| `auth:session:id:*` | 登录成功 | refresh/absolute 剩余 | Session 快照 |
| `auth:session:jti:*` | 登录成功 | 同上 | jti 索引 |

QQ 的 `access_token` **一般不必存入 Redis/MySQL**（仅用于换 openid，用完即可）。

---

## 九、数据库设计参考

### 9.1 扩展 auth_user_identity

```sql
-- identity_type 建议扩展 ENUM，或新增 oauth 表
identity_type  = 'qq_openid'   -- 或 'qq_unionid'
identity_value = openid 或 unionid
verified       = 1
```

### 9.2 登录审计

```sql
auth_login_audit.login_type = 'qq_oauth'
```

---

## 十、SecurityConfig 需放行的路径

```mermaid
flowchart LR
    Public[公开路径 permitAll]
    Public --> P1["api auth oauth qq state"]
    Public --> P2["api auth oauth qq authorize"]
    Public --> P3["api auth oauth qq callback"]
```

与现有 `/api/auth/login`、`/refresh` 一样，OAuth 入口和 callback **不能**走 JWT 过滤器。

---

## 十一、时序对比：扫码 vs 账号密码

```mermaid
flowchart TB
    subgraph 密码登录["现有 密码登录"]
        P1[POST login JSON] --> P2[BCrypt 校验]
        P2 --> P3[createSession]
    end

    subgraph QQ扫码["QQ 扫码 OAuth"]
        Q1[SDK 展示二维码] --> Q2[QQ 回调 code]
        Q2 --> Q3[换 openid]
        Q3 --> Q4[查或建用户]
        Q4 --> Q5[createSession]
    end

    P3 --> Same[同一套双 Token + Session]
    Q5 --> Same
```

**差异只在「如何证明用户是谁」；Session 与 Token 签发逻辑可完全复用。**

---

## 十二、异常与失败分支

```mermaid
flowchart TD
    A[扫码登录] --> B{state 校验}
    B -->|失败| F1["拒绝 疑似 CSRF"]
    B -->|通过| C{code 换 token}
    C -->|失败| F2["code 过期或已使用"]
    C -->|成功| D{openid 绑定}
    D -->|账号被禁用| F3[403 用户禁用]
    D -->|正常| OK[登录成功]

    E[用户取消扫码] --> F4[前端提示 刷新二维码]
    G[二维码过期] --> F4
```

| 场景 | 前端 | 后端 |
|------|------|------|
| 用户取消 | SDK 回调失败，提示重试 | 无请求 |
| state 不匹配 | — | 400，记录安全日志 |
| code 无效 | 跳转错误页 | 401 |
| QQ API 超时 | 提示稍后重试 | 503 + 重试 |

---

## 十三、配置项参考

```yaml
auth:
  oauth:
    qq:
      app-id: ${QQ_APP_ID}
      app-key: ${QQ_APP_KEY}
      redirect-uri: https://your-domain.com/api/auth/oauth/qq/callback
      scope: get_user_info
      state-ttl-seconds: 600
      frontend-success-url: https://your-frontend.com/oauth/success
```

---

## 十四、实施 checklist

- [ ] QQ 互联创建网站应用，配置 redirect_uri
- [ ] 后端：`/oauth/qq/state`、`/oauth/qq/callback`
- [ ] Redis：`auth:oauth:state:*`
- [ ] MySQL：`auth_user_identity` 支持 qq_openid
- [ ] `SecurityConfig` 放行 OAuth 路径
- [ ] 登录成功复用 `createSessionAndResponse`
- [ ] 前端：引入 JS SDK，展示二维码容器
- [ ] callback 后重定向前端并保存双 Token
- [ ] 审计 `login_type=qq_oauth`
- [ ] 联调：扫码 → `/api/user/me` → refresh → logout

---

## 十五、相关链接

| 资源 | 地址 |
|------|------|
| QQ 互联首页 | https://connect.qq.com/ |
| 开发文档 | https://wiki.connect.qq.com/ （以官网最新为准） |
| 本项目接口文档 | [接口文档](./接口文档.md) |
| 登录全链路说明 | [登录流程学习文档](./登录流程学习文档.md) |

---

## 十六、总结

```text
QQ PC 扫码登录 = QQ JS SDK 展示二维码
                + 手机 QQ 确认授权
                + OAuth 2.0 授权码换 openid
                + 映射到本地用户
                + 复用现有双 Token Session
```

**扫码只是交互；安全与账号体系的关键仍是：state 校验、code 一次性、openid 绑定、双 Token 签发。**

如需在本仓库落地 QQ OAuth 代码，可参考本文接口设计，在 Agent 模式下实现 Controller / Service / 表结构变更。
