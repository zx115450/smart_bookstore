-- 秒杀入口令牌桶：按时间补令牌，原子 try-acquire
-- KEYS[1] = seckill:bucket:...
-- ARGV[1] = capacity（桶容量 b）
-- ARGV[2] = refill_per_second（每秒补充速率 r）
-- ARGV[3] = now_ms（当前毫秒时间戳）
-- ARGV[4] = acquire（本次领取数量，通常 1）
-- ARGV[5] = ttl_seconds（Key 过期，避免冷活动残留）
-- 返回：1=拿到令牌  0=桶空拒绝
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local acquire = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

if capacity == nil or capacity <= 0 or rate == nil or rate < 0
        or now == nil or acquire == nil or acquire <= 0 or ttl == nil or ttl <= 0 then
    return 0
end

local data = redis.call('HMGET', key, 'tokens', 'last_ms')
local tokens = tonumber(data[1])
local last = tonumber(data[2])

if tokens == nil or last == nil then
    tokens = capacity
    last = now
end

local elapsed = now - last
if elapsed < 0 then
    elapsed = 0
end

local filled = tokens + (elapsed * rate / 1000.0)
if filled > capacity then
    filled = capacity
end

if filled < acquire then
    redis.call('HSET', key, 'tokens', filled, 'last_ms', now)
    redis.call('EXPIRE', key, ttl)
    return 0
end

filled = filled - acquire
redis.call('HSET', key, 'tokens', filled, 'last_ms', now)
redis.call('EXPIRE', key, ttl)
return 1
