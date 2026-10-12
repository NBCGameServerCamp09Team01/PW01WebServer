-- 다시 채우기 시작. 잠금을 얻고 빈 임시 보드(@만 있음)를 만든다.
-- KEYS[1] = pw01:rank-lock:<보드>, KEYS[2] = pw01:rank:<보드>:build, KEYS[3] = pw01:rank:<보드>:build:member
-- ARGV[1] = 서버 토큰, ARGV[2] = 잠금 시간(초), ARGV[3] = 임시 보드 수명(초)
-- 돌려줌: 1 = 시작, 0 = 다른 서버가 채우는 중
if not redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
  return 0
end
redis.call('DEL', KEYS[2], KEYS[3])
redis.call('HSET', KEYS[3], '@', '1')
redis.call('EXPIRE', KEYS[3], ARGV[3])
return 1
