-- 명세: 루트 docs/contracts/redis-keys.md "채팅" 절. 고칠 때는 명세를 먼저 고친다.
-- 채팅 횟수 제한과 도배 쉬기를 한 번에 판단한다(읽기-판단-쓰기를 한 스크립트로 — 동시 요청·서버 여러 대에도 수가 맞음).
-- KEYS[1] = pw01:chat-rate:<계정 ID>, KEYS[2] = pw01:chat-strike:<계정 ID>, KEYS[3] = pw01:chat-mute:<계정 ID>
-- ARGV[1] = 창(밀리초), ARGV[2] = 창 안 최대 수, ARGV[3] = 경고 창(밀리초), ARGV[4] = 쉬기에 들어가는 경고 수, ARGV[5] = 쉬는 시간(밀리초)
-- 돌려줌: {0, 0} 통과 / {1, 남은 밀리초} 횟수 제한 / {2, 남은 밀리초} 쉬는 중(이번에 시작했으면 쉬는 시간 전체)
local mute = redis.call('PTTL', KEYS[3])
if mute > 0 then
  return {2, mute}
end
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
if n <= tonumber(ARGV[2]) then
  return {0, 0}
end
if n == tonumber(ARGV[2]) + 1 then
  local s = redis.call('INCR', KEYS[2])
  if s == 1 then
    redis.call('PEXPIRE', KEYS[2], ARGV[3])
  end
  if s >= tonumber(ARGV[4]) then
    redis.call('SET', KEYS[3], '1', 'PX', ARGV[5])
    redis.call('DEL', KEYS[2])
    return {2, tonumber(ARGV[5])}
  end
end
return {1, redis.call('PTTL', KEYS[1])}
