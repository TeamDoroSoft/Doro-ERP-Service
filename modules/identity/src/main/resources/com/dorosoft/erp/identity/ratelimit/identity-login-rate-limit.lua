-- Doro ERP Identity login token bucket.
--
-- KEYS: active login, active IP, previous login, previous IP, shared event marker.
-- ARGV: mode, nowMillis, hasPrevious, loginCapacity, loginIntervalMillis,
--       ipCapacity, ipIntervalMillis, tokenScale, markerTtlMillis, expectedWindowSeed.
--
-- All bucket reads, discrete refill, dual-generation synchronization, all-or-nothing
-- consumption, TTL, and NONE/PENDING/RECORDED event-marker transitions are atomic.

local mode = ARGV[1]
local now = tonumber(ARGV[2])
local hasPrevious = tonumber(ARGV[3]) == 1
local loginCapacity = tonumber(ARGV[4]) * tonumber(ARGV[8])
local loginInterval = tonumber(ARGV[5])
local ipCapacity = tonumber(ARGV[6]) * tonumber(ARGV[8])
local ipInterval = tonumber(ARGV[7])
local scale = tonumber(ARGV[8])
local markerTtl = tonumber(ARGV[9])
local expectedWindow = ARGV[10]

local function marker_result(success)
    local window = redis.call('HGET', KEYS[5], 'window')
    if not window then
        return {success, 0, 0, ''}
    end
    return {success, 0, 0, window}
end

if mode == 'INSPECT' then
    if redis.call('EXISTS', KEYS[5]) == 0 then
        return {0, 0, 0, ''}
    end
    return marker_result(1)
end

if mode == 'ACK' then
    local window = redis.call('HGET', KEYS[5], 'window')
    if not window or window ~= expectedWindow then
        return {0, 0, 0, window or ''}
    end
    redis.call('HSET', KEYS[5], 'state', 'RECORDED')
    redis.call('PEXPIRE', KEYS[5], markerTtl)
    return {1, 0, 0, window}
end

if mode ~= 'EVAL' then
    return redis.error_reply('unsupported mode')
end

local function load_bucket(key, capacity, interval)
    local values = redis.call('HMGET', key, 'tokens', 'last')
    local tokens = tonumber(values[1])
    local last = tonumber(values[2])
    if not tokens or not last then
        return capacity, now
    end

    local elapsed = now - last
    if elapsed < 0 then
        elapsed = 0
    end
    local steps = math.floor(elapsed / interval)
    if steps > 0 then
        tokens = math.min(capacity, tokens + (steps * scale))
        if tokens == capacity then
            last = now
        else
            last = last + (steps * interval)
        end
    end
    return tokens, last
end

local function save_bucket(key, tokens, last, ttl)
    redis.call('HSET', key, 'tokens', tokens, 'last', last)
    redis.call('PEXPIRE', key, ttl)
end

local function wait_millis(tokens, last, interval)
    if tokens >= scale then
        return 0
    end
    local elapsed = now - last
    if elapsed < 0 then
        elapsed = 0
    end
    return math.max(1, interval - elapsed)
end

local activeLoginTokens, activeLoginLast = load_bucket(KEYS[1], loginCapacity, loginInterval)
local activeIpTokens, activeIpLast = load_bucket(KEYS[2], ipCapacity, ipInterval)
local loginTokens = activeLoginTokens
local loginLast = activeLoginLast
local ipTokens = activeIpTokens
local ipLast = activeIpLast

if hasPrevious then
    local previousLoginTokens, previousLoginLast = load_bucket(KEYS[3], loginCapacity, loginInterval)
    local previousIpTokens, previousIpLast = load_bucket(KEYS[4], ipCapacity, ipInterval)
    loginTokens = math.min(activeLoginTokens, previousLoginTokens)
    loginLast = math.max(activeLoginLast, previousLoginLast)
    ipTokens = math.min(activeIpTokens, previousIpTokens)
    ipLast = math.max(activeIpLast, previousIpLast)
end

if loginTokens == loginCapacity and ipTokens == ipCapacity then
    redis.call('DEL', KEYS[5])
end

local loginWait = wait_millis(loginTokens, loginLast, loginInterval)
local ipWait = wait_millis(ipTokens, ipLast, ipInterval)
local allowed = loginWait == 0 and ipWait == 0

if allowed then
    loginTokens = loginTokens - scale
    ipTokens = ipTokens - scale
end

save_bucket(KEYS[1], loginTokens, loginLast, (tonumber(ARGV[4]) + 1) * loginInterval)
save_bucket(KEYS[2], ipTokens, ipLast, (tonumber(ARGV[6]) + 1) * ipInterval)
if hasPrevious then
    save_bucket(KEYS[3], loginTokens, loginLast, (tonumber(ARGV[4]) + 1) * loginInterval)
    save_bucket(KEYS[4], ipTokens, ipLast, (tonumber(ARGV[6]) + 1) * ipInterval)
end

if allowed then
    return {1, 0, 0, ''}
end

local retryMillis = math.max(loginWait, ipWait)
local retrySeconds = math.max(1, math.ceil(retryMillis / 1000))
local state = redis.call('HGET', KEYS[5], 'state')
local window = redis.call('HGET', KEYS[5], 'window')
local eventRequired = 0

if not state or not window then
    state = 'PENDING'
    window = tostring(now)
    redis.call('HSET', KEYS[5], 'state', state, 'window', window)
    eventRequired = 1
elseif state == 'PENDING' then
    eventRequired = 1
elseif state ~= 'RECORDED' then
    return redis.error_reply('invalid marker state')
end
redis.call('PEXPIRE', KEYS[5], markerTtl)

return {0, retrySeconds, eventRequired, window}
