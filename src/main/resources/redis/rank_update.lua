-- 한 계정의 줄을 보드에 넣는다. 그 계정의 지금 줄보다 좋을 때만 바꾼다(ZSet 점수 = −보드 점수가 작을수록, 같으면 멤버 문자 순서).
-- 보드가 있으면 보드에, 다시 채우는 중이면 임시 보드에도 넣는다. 둘 다 없으면 넣지 않는다.
-- KEYS[1] = pw01:rank:<보드>          KEYS[2] = pw01:rank:<보드>:member
-- KEYS[3] = pw01:rank:<보드>:build    KEYS[4] = pw01:rank:<보드>:build:member
-- ARGV[1] = 계정 ID, ARGV[2] = 멤버, ARGV[3] = ZSet 점수(−보드 점수)
-- ARGV[4] = 보드 만료 시각(epoch 초). 0이면 만료를 건드리지 않음(서버는 늘 값을 넘김)
-- 돌려줌: 1 = 바꿈, 0 = 그대로(더 좋지 않음), -1 = 보드가 없어 넣지 않음
local function put(z, h)
  local score = tonumber(ARGV[3])
  local old = redis.call('HGET', h, ARGV[1])
  if old then
    local oldScore = tonumber(redis.call('ZSCORE', z, old))
    if oldScore and (score > oldScore or (score == oldScore and ARGV[2] >= old)) then
      return 0
    end
    redis.call('ZREM', z, old)
  end
  redis.call('ZADD', z, score, ARGV[2])
  redis.call('HSET', h, ARGV[1], ARGV[2])
  return 1
end

local result = -1
if redis.call('HEXISTS', KEYS[2], '@') == 1 then
  result = put(KEYS[1], KEYS[2])
  if ARGV[4] ~= '0' then
    redis.call('EXPIREAT', KEYS[1], ARGV[4])
    redis.call('EXPIREAT', KEYS[2], ARGV[4])
  end
end
if redis.call('HEXISTS', KEYS[4], '@') == 1 then
  local r = put(KEYS[3], KEYS[4])
  local ttl = redis.call('PTTL', KEYS[4])
  if ttl > 0 then
    redis.call('PEXPIRE', KEYS[3], ttl)
  end
  if result == -1 then
    result = r
  end
end
return result
