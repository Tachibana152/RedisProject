-- 秒杀脚本：原子完成「时间判断 + 库存判断 + 扣减 + 一人一单判断 + 发送消息」
-- ARGV[1] = voucherId 优惠券id
-- ARGV[2] = userId 用户id
-- ARGV[3] = 当前时间戳(ms)
-- ARGV[4] = 秒杀开始时间戳(ms)
-- ARGV[5] = 秒杀结束时间戳(ms)
-- ARGV[6] = orderId 订单id
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 时间校验：未开始或已结束返回 3
if tonumber(ARGV[3]) < tonumber(ARGV[4]) or tonumber(ARGV[3]) > tonumber(ARGV[5]) then
    return 3
end

-- 库存判断：key 不存在时按 0 处理（nil 容错，避免报错）
local stock = tonumber(redis.call('get', stockKey) or '0')
if stock <= 0 then
    -- 库存不足
    return 1
end

-- 一人一单：该用户是否已在秒杀订单集合中
if redis.call('sismember', orderKey, userId) == 1 then
    -- 重复下单
    return 2
end

-- 扣库存 + 记录用户（原子执行）
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- 抢购资格认定成功：向 stream.orders 发送消息，内容包含 voucherId、userId、orderId
redis.call('xadd', 'stream.orders', '*', 'voucherId', voucherId, 'userId', userId, 'id', ARGV[6])
return 0
