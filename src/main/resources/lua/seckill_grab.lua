-- P4 秒杀抢券 Lua 脚本：判库存、判重复、扣库存、记录用户，一次原子完成
-- KEYS[1] = seckill:stock:{activityId}   剩余库存
-- KEYS[2] = seckill:users:{activityId}   已抢用户 Set
-- ARGV[1] = userId
-- 返回值：0=售罄  1=抢中待发 MQ  2=用户已参与
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return 0
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 1
