-- 명세: 루트 docs/contracts/redis-keys.md "접속 상태" 절(v1.6). 고칠 때는 명세를 먼저 고친다.
-- 접속 상태 지우기(끊김).
-- KEYS[1] = pw01:presence:<계정>, ARGV[1] = 이 서버 번호
-- 값이 이 서버 번호일 때만 지운다(그사이 다른 서버로 다시 붙었으면 그 값은 남긴다). 지웠으면 1, 아니면 0
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
