-- keys: [1]req_total, [2]latency_sum, [3]err_count, [4]qps, [5]route_rank
-- argv: [1]latency, [2]is_error(1/0), [3]route_id

local key_req = KEYS[1]
local key_lat = KEYS[2]
local key_err = KEYS[3]
local key_qps = KEYS[4]
local key_rank = KEYS[5]

local lat = tonumber(ARGV[1])
local is_err = tonumber(ARGV[2])
local route_id = ARGV[3]

-- 1. 总请求数 (5分钟过期)
redis.call('INCR', key_req)
redis.call('EXPIRE', key_req, 300)

-- 2. 耗时累计
redis.call('INCRBY', key_lat, lat)
redis.call('EXPIRE', key_lat, 300)

-- 3. 错误数
if is_err == 1 then
    redis.call('INCR', key_err)
    redis.call('EXPIRE', key_err, 300)
end

-- 4. 实时 QPS (1分钟过期)
redis.call('INCR', key_qps)
redis.call('EXPIRE', key_qps, 60)

-- 5. 路由排行
if route_id ~= nil and route_id ~= 'null' then
    redis.call('ZINCRBY', key_rank, 1, route_id)
    -- 可选：防止无限膨胀，定期修剪或设置过期
    redis.call('EXPIRE', key_rank, 604800) -- 7天
end

return 1