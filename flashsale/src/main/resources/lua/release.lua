-- 支付超时释放库存：仅当该顾客确实持有名额时才释放（幂等）
-- KEYS[1]=users  KEYS[2]=inv  KEYS[3]=order:{customerId}
-- ARGV[1]=customerId  ARGV[2]=stock(封顶)
local removed = redis.call('SREM', KEYS[1], ARGV[1])
if removed == 1 then
  local n = tonumber(redis.call('GET', KEYS[2]) or '0')
  n = math.min(n + 1, tonumber(ARGV[2]))
  redis.call('SET', KEYS[2], n)
  redis.call('DEL', KEYS[3])
  return n
end
return -1  -- 该顾客不持有名额（重复释放无害）
