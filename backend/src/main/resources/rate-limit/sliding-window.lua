local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local request_id = ARGV[3]

local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) * 1000 + math.floor(tonumber(redis_time[2]) / 1000)
local cutoff = now - window_ms

redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)
local count = redis.call('ZCARD', key)
if count >= limit then
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_ms = math.max(1, tonumber(oldest[2]) + window_ms - now)
    return {0, 0, retry_ms}
end

redis.call('ZADD', key, now, now .. ':' .. request_id)
redis.call('PEXPIRE', key, window_ms)
return {1, limit - count - 1, 0}
