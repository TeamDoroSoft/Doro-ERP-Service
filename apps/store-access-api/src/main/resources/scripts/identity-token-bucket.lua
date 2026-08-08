-- Atomic Redis token bucket for login rate limiting (ADR-02-007).
-- KEYS[1] = bucket key
-- ARGV[1] = capacity
-- ARGV[2] = refill tokens per interval
-- ARGV[3] = refill interval, milliseconds
-- ARGV[4] = current time, epoch milliseconds
-- ARGV[5] = bucket TTL, seconds

local capacity = tonumber(ARGV[1])
local refillTokens = tonumber(ARGV[2])
local refillIntervalMs = tonumber(ARGV[3])
local now = tonumber(ARGV[4])
local ttlSeconds = tonumber(ARGV[5])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(bucket[1])
local timestamp = tonumber(bucket[2])

if tokens == nil then
  tokens = capacity
  timestamp = now
end

local elapsed = math.max(0, now - timestamp)
local refilled = (elapsed / refillIntervalMs) * refillTokens
tokens = math.min(capacity, tokens + refilled)

local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

redis.call('HMSET', KEYS[1], 'tokens', tostring(tokens), 'timestamp', tostring(now))
redis.call('EXPIRE', KEYS[1], ttlSeconds)

return allowed
