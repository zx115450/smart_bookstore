# 管理端 Apifox 接口集合

> 用于 Step 5.1 管理端联调与答辩演示。导入 Apifox 时按目录创建即可；所有管理接口需 **ADMIN** 角色 Token。

## 环境变量

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | 后端地址 |
| `adminToken` | `eyJ...` | admin 登录后的 accessToken |

## 前置：管理员登录

```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "account": "admin",
  "password": "123456",
  "loginType": "PASSWORD"
}
```

从响应 `data.accessToken` 复制到环境变量 `adminToken`。  
请求头统一：`Authorization: Bearer {{adminToken}}`

---

## 1. 图书与分类

### 1.1 分类列表（含禁用）

```http
GET {{baseUrl}}/api/admin/book-categories
Authorization: Bearer {{adminToken}}
```

### 1.2 新建分类

```http
POST {{baseUrl}}/api/admin/book-categories
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "技术",
  "sort": 1
}
```

### 1.3 更新分类

```http
PUT {{baseUrl}}/api/admin/book-categories/1
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "计算机",
  "sort": 1,
  "status": 1
}
```

### 1.4 禁用分类

```http
DELETE {{baseUrl}}/api/admin/book-categories/1
Authorization: Bearer {{adminToken}}
```

### 1.5 图书列表（含下架）

```http
GET {{baseUrl}}/api/admin/books?keyword=&status=&page=1&size=10
Authorization: Bearer {{adminToken}}
```

### 1.6 上架图书

```http
POST {{baseUrl}}/api/admin/books
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "categoryId": 1,
  "title": "Java 核心技术",
  "author": "Cay S. Horstmann",
  "price": 89.00,
  "saleStock": 100,
  "borrowStock": 5,
  "borrowDays": 30,
  "description": "经典 Java 教材"
}
```

### 1.7 更新 / 下架图书

```http
PUT {{baseUrl}}/api/admin/books/1
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "price": 79.00,
  "saleStock": 120
}
```

```http
DELETE {{baseUrl}}/api/admin/books/1
Authorization: Bearer {{adminToken}}
```

---

## 2. 券模板

### 2.1 列表

```http
GET {{baseUrl}}/api/admin/coupon-templates?status=1&page=1&size=10
Authorization: Bearer {{adminToken}}
```

### 2.2 创建满减券

```http
POST {{baseUrl}}/api/admin/coupon-templates
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "满100减20",
  "couponType": "FIXED",
  "thresholdAmount": 100.00,
  "discountAmount": 20.00,
  "totalCount": 500,
  "validDays": 14
}
```

### 2.3 更新 / 禁用

```http
PUT {{baseUrl}}/api/admin/coupon-templates/2
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "totalCount": 1000
}
```

```http
DELETE {{baseUrl}}/api/admin/coupon-templates/2
Authorization: Bearer {{adminToken}}
```

---

## 3. 秒杀活动

### 3.1 创建活动（自动预热 Redis）

```http
POST {{baseUrl}}/api/admin/seckill/activities
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "周末抢券",
  "templateId": 2,
  "seckillStock": 100,
  "startTime": "2026-07-11 10:00:00",
  "endTime": "2026-07-11 12:00:00"
}
```

### 3.2 列表 / 详情 / 更新 / 禁用

```http
GET {{baseUrl}}/api/admin/seckill/activities
Authorization: Bearer {{adminToken}}
```

```http
GET {{baseUrl}}/api/admin/seckill/activities/1
Authorization: Bearer {{adminToken}}
```

```http
PUT {{baseUrl}}/api/admin/seckill/activities/1
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "name": "周末抢券（延期）",
  "endTime": "2026-07-11 14:00:00"
}
```

```http
DELETE {{baseUrl}}/api/admin/seckill/activities/1
Authorization: Bearer {{adminToken}}
```

---

## 4. 订单查询

### 4.1 预约订单

```http
GET {{baseUrl}}/api/reservation/admin/orders?status=&page=1&size=10
Authorization: Bearer {{adminToken}}
```

### 4.2 借阅订单

```http
GET {{baseUrl}}/api/admin/borrow/orders?status=APPLIED&page=1&size=10
Authorization: Bearer {{adminToken}}
```

馆员操作：

```http
POST {{baseUrl}}/api/admin/borrow/orders/1/confirm
Authorization: Bearer {{adminToken}}
```

```http
POST {{baseUrl}}/api/admin/borrow/orders/1/reject
Authorization: Bearer {{adminToken}}
```

```http
POST {{baseUrl}}/api/admin/borrow/orders/1/return
Authorization: Bearer {{adminToken}}
```

### 4.3 购书订单

```http
GET {{baseUrl}}/api/admin/trade/orders?status=&page=1&size=10
Authorization: Bearer {{adminToken}}
```

---

## 5. 演示脚本（答辩用）

1. admin 登录 → 创建分类 → 上架图书  
2. 创建券模板 → 创建秒杀活动  
3. 查看预约 / 借阅 / 购书订单列表  
4. 用普通 user 账号走用户端流程，再回到管理端查订单  

## 权限说明

- `/api/admin/**` 与 `/api/reservation/admin/**` 需 JWT 含 `ADMIN` 角色  
- 普通 user 访问上述路径返回 `403`（code=3001）  
- 测试账号：`admin` / `123456`（schema.sql 种子数据）
