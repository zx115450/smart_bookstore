-- 借阅逾期：原子取出 score <= maxScore 的 orderId 并从 ZSET 删除
-- KEYS[1] = borrow:due:zset
-- ARGV[1] = maxScore（当前 Unix 秒）
-- ARGV[2] = limit（批次大小）
-- 返回：被弹出的 member 列表（可能为空 table）
local key = KEYS[1]
local maxScore = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
if maxScore == nil or limit == nil or limit <= 0 then
    return {}
end

local members = redis.call('ZRANGEBYSCORE', key, '-inf', maxScore, 'LIMIT', 0, limit)
if #members == 0 then
    return {}
end

for i = 1, #members do
    redis.call('ZREM', key, members[i])
end

return members
