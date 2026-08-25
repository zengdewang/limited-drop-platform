-- 抢购原子脚本：库存扣减 + 每用户幂等，一次往返（无超卖、无 TOCTOU）
-- KEYS[1]=inv  KEYS[2]=users  KEYS[3]=open  KEYS[4]=order:{customerId}
-- ARGV[1]=customerId  ARGV[2]=qty  ARGV[3]=orderNo  ARGV[4]=ttlMillis
local exists = redis.call('EXISTS', KEYS[3])
if exists == 0 then
  return -3  -- 未开售 / 已结束
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return -2  -- 重复抢购（幂等）
end
local inv = tonumber(redis.call('GET', KEYS[1]) or '0')
if inv < tonumber(ARGV[2]) then
  return -1  -- 售罄
end
redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('SET', KEYS[4], ARGV[3])
redis.call('PEXPIRE', KEYS[1], ARGV[4])
redis.call('PEXPIRE', KEYS[2], ARGV[4])
redis.call('PEXPIRE', KEYS[4], ARGV[4])
return inv - tonumber(ARGV[2])  -- 成功，返回剩余库存
