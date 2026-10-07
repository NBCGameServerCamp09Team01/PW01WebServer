-- 명세: 루트 docs/contracts/redis-keys.md "스크립트" 절. 고칠 때는 명세를 먼저 고친다.
-- 로그인 실패를 하나 센다(INCR과 EXPIRE를 한 번에).
-- 첫 실패에 "실패를 세는 시간"을 TTL로 걸어 그 안의 실패만 센다. 허용 횟수에 닿으면 그때부터 잠김 시간으로 TTL을 다시 건다.
-- KEYS[1] = pw01:login-fail:<아이디(입력 그대로)>
-- ARGV[1] = 허용 실패 횟수, ARGV[2] = 실패를 세는 시간(초), ARGV[3] = 잠김 시간(초)
-- 돌려줌: {실패 횟수, 남은 잠김 초}. 잠기지 않았으면 남은 잠김 초는 0
local count = redis.call('INCR', KEYS[1])
local max = tonumber(ARGV[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[2])
end
if count < max then
  return {count, 0}
end
if count == max then
  redis.call('EXPIRE', KEYS[1], ARGV[3])
end
return {count, redis.call('TTL', KEYS[1])}
