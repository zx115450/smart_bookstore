-- wrk 脚本示意：POST 秒杀抢券（请替换 Token 与 URL）
-- 用法见同目录 README.md
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer REPLACE_ME"

request = function()
  local key = "perf-" .. tostring(math.random(1, 100000000))
  local body = '{"idempotencyKey":"' .. key .. '"}'
  return wrk.format(nil, nil, nil, body)
end
