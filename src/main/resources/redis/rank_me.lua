-- 내 순위.
-- KEYS[1] = pw01:rank:<보드>, KEYS[2] = pw01:rank:<보드>:member
-- ARGV[1] = 계정 ID
-- 돌려줌: 보드가 없으면 nil / 기록이 없으면 {전체 수} / {전체 수, 순위(0부터), 멤버, 점수}
if redis.call('HEXISTS', KEYS[2], '@') == 0 then
  return false
end
local total = redis.call('ZCARD', KEYS[1])
local m = redis.call('HGET', KEYS[2], ARGV[1])
if not m then
  return {total}
end
local r = redis.call('ZRANK', KEYS[1], m)
if not r then
  return {total}
end
return {total, r, m, redis.call('ZSCORE', KEYS[1], m)}
