-- MySQL에서 읽은 기록 묶음을 임시 보드에 넣는다. 비교 규칙은 rank_update.lua와 같다.
-- KEYS[1] = pw01:rank:<보드>:build, KEYS[2] = pw01:rank:<보드>:build:member
-- ARGV = 계정 ID, 멤버, ZSet 점수(−보드 점수)를 한 줄씩 이어 붙임(줄 수 × 3)
-- 돌려줌: 바꾼 줄 수, 임시 보드가 사라졌으면 -1
if redis.call('HEXISTS', KEYS[2], '@') == 0 then
  return -1
end
local changed = 0
for i = 1, #ARGV, 3 do
  local id, member, score = ARGV[i], ARGV[i + 1], tonumber(ARGV[i + 2])
  local old = redis.call('HGET', KEYS[2], id)
  local oldScore = old and tonumber(redis.call('ZSCORE', KEYS[1], old))
  if not (oldScore and (score > oldScore or (score == oldScore and member >= old))) then
    if old then
      redis.call('ZREM', KEYS[1], old)
    end
    redis.call('ZADD', KEYS[1], score, member)
    redis.call('HSET', KEYS[2], id, member)
    changed = changed + 1
  end
end
local ttl = redis.call('PTTL', KEYS[2])
if ttl > 0 then
  redis.call('PEXPIRE', KEYS[1], ttl)
end
return changed
