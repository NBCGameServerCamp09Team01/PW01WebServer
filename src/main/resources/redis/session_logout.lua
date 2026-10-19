-- 명세: 루트 docs/contracts/redis-keys.md "스크립트" 절. 고칠 때는 명세를 먼저 고친다.
-- 로그아웃. 내 세션 키는 늘 지우고, 계정 키는 지금 값이 내 해시일 때만 지운다.
-- (다른 곳에서 로그인한 뒤 이전 기기의 로그아웃이 새 기기의 세션을 지우지 않게)
-- KEYS[1] = pw01:session:<토큰 해시>
-- KEYS[2] = pw01:account-session:<계정 ID>
-- ARGV[1] = 토큰 해시
-- 돌려줌: 1 = 두 키를 지움, 0 = 내 세션 키만 지움(계정 키는 다른 토큰 것이거나 없음)
redis.call('DEL', KEYS[1])
if redis.call('GET', KEYS[2]) == ARGV[1] then
  redis.call('DEL', KEYS[2])
  return 1
end
return 0
