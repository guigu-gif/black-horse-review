-- 秒杀原子判断脚本
-- KEYS[1]: 库存key  seckill:stock:{voucherId}
-- KEYS[2]: 已购用户set key  seckill:order:{voucherId}
-- ARGV[1]: 用户ID

-- 1. 判断库存
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return 1  -- 库存不足
end

-- 2. 判断是否已购买
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2  -- 已购买过
end

-- 3. 扣库存，记录用户（两步打包在一个脚本里，Redis保证原子性）
redis.call('incrby', KEYS[1], -1)
redis.call('sadd', KEYS[2], ARGV[1])
return 0  -- 成功
