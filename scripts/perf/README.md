# 秒杀压测草稿（可选）

在单测 / 集成测通过后，可用本说明对线上或预发做一轮冒烟压测。目标不是刷分，而是留下 **可重复的终态证据**：成功率、库存、一人一单。

---

## 前置

1. 已登录拿到 Access Token
2. 管理端创建秒杀活动，记下 `activityId`，库存设为 `N`（如 50）
3. Redis 已预热（创建活动时会 `warmUpStock`）
4. 后端可达，例如 `http://localhost:8081`

---

## wrk 示例（需自行编译 Lua 脚本发 JSON）

wrk 默认不便带动态 `idempotencyKey`，更推荐用 JMeter / 自写脚本。若仅测「已参与后的拒绝」，可固定一个 key。

```bash
# 安装 wrk 后（仅示意：需配合 post.lua 设置 Header / Body）
wrk -t4 -c50 -d10s -s scripts/perf/seckill_grab.lua \
  http://localhost:8081/api/seckill/activities/1/grab
```

`scripts/perf/seckill_grab.lua` 示意：

```lua
-- 每次请求换幂等键，避免全打到同一 idempotencyKey
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer <TOKEN>"

request = function()
  local key = "perf-" .. tostring(math.random(1, 100000000))
  local body = '{"idempotencyKey":"' .. key .. '"}'
  return wrk.format(nil, nil, nil, body)
end
```

注意：多用户才有意义；同一 Token 同一用户会被 Redis Set / DB 一人一单拦住，QPS 高但成功数最多 1。

---

## 更靠谱：多用户脚本思路

1. 准备 `M` 个测试账号 Token（`M > N`）
2. 并发对同一 `activityId` 调用 `POST /api/seckill/activities/{id}/grab`
3. 结束后统计：
   - HTTP 成功且最终 `getResult` 为 `SUCCESS` 的用户数 `≤ N`
   - Redis `seckill:stock:{id}` 剩余 ≥ 0
   - `seckill_order` 中该活动成功单数 `≤ N`，且 `(user_id, activity_id)` 无重复

---

## 记录模板

| 项 | 值 |
| --- | --- |
| 时间 | |
| 库存 N | |
| 并发用户 / 线程 | |
| 工具 | wrk / JMeter / 自研 |
| SUCCESS 数 | |
| Redis 剩余库存 | |
| DB 成功单数 | |
| 是否超卖 | 是 / 否 |
| 备注 | |

与集成测 `SeckillRedisGrabIT` 互补：IT 证 Lua 层；压测证整条 HTTP + MQ + DB 链路。
