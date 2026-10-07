-- 명세: 루트 docs/contracts/redis-keys.md "스크립트" 절. 고칠 때는 명세를 먼저 고친다.
-- 인증이 필요한 요청마다(접속 점검 포함) 두 키의 TTL을 함께 늘린다.
-- 계정 키가 지금 내 해시일 때만 늘린다(그사이 다른 곳에서 로그인했으면 늘리지 않음).
-- KEYS[1] = pw01:session:<토큰 해시>
-- KEYS[2] = pw01:account-session:<계정 ID>
-- ARGV[1] = 토큰 해시, ARGV[2] = 세션 수명(초)
-- 돌려줌: 1 = 늘림, 0 = 세션 없음(세션 키나 계정 키가 없음), 2 = 다른 곳에서 로그인
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local current = redis.call('GET', KEYS[2])
if not current then
  return 0
end
if current ~= ARGV[1] then
  return 2
end
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('EXPIRE', KEYS[2], ARGV[2])
return 1
