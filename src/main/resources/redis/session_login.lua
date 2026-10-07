-- 명세: 루트 docs/contracts/redis-keys.md "스크립트" 절. 고칠 때는 명세를 먼저 고친다.
-- 로그인 성공 뒤 세션 만들기. 두 키를 한 번에 쓴다(가운데서 멈추면 새 토큰이 "다른 곳에서 로그인"이 되는 일을 막음).
-- KEYS[1] = pw01:session:<새 토큰 해시>
-- KEYS[2] = pw01:account-session:<계정 ID>
-- ARGV[1] = 계정 ID, ARGV[2] = 새 토큰 해시, ARGV[3] = 세션 수명(초)
-- 돌려줌: 이전 토큰 해시(없으면 빈 문자열). 이전 세션 키는 지우지 않고 TTL까지 둔다 → 이전 기기는 AUTH_SESSION_REPLACED
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
local previous = redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3], 'GET')
if not previous then
  return ''
end
return previous
