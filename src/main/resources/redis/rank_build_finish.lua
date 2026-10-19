-- 다시 채우기 끝. 잠금이 아직 내 것이고 임시 보드가 살아 있으면 보드를 통째로 바꿔 끼운다.
-- KEYS[1] = pw01:rank:<보드>, KEYS[2] = pw01:rank:<보드>:member
-- KEYS[3] = pw01:rank:<보드>:build, KEYS[4] = pw01:rank:<보드>:build:member, KEYS[5] = pw01:rank-lock:<보드>
-- ARGV[1] = 서버 토큰, ARGV[2] = 보드 만료 시각(epoch 초). 0이면 만료 없음(서버는 늘 값을 넘김)
-- 돌려줌: 1 = 바꿔 끼움, 0 = 잠금을 잃었거나 임시 보드가 만료됨(바꾸지 않음)
if redis.call('GET', KEYS[5]) ~= ARGV[1] or redis.call('HEXISTS', KEYS[4], '@') == 0 then
  return 0
end
if redis.call('EXISTS', KEYS[3]) == 1 then
  redis.call('RENAME', KEYS[3], KEYS[1])
else
  redis.call('DEL', KEYS[1])
end
redis.call('RENAME', KEYS[4], KEYS[2])
if ARGV[2] == '0' then
  redis.call('PERSIST', KEYS[1])
  redis.call('PERSIST', KEYS[2])
else
  redis.call('EXPIREAT', KEYS[1], ARGV[2])
  redis.call('EXPIREAT', KEYS[2], ARGV[2])
end
redis.call('DEL', KEYS[5])
return 1
