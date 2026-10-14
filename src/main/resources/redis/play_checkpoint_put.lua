-- 명세: 루트 docs/contracts/redis-keys.md "플레이 체크포인트(v1.4)" 절. 고칠 때는 명세를 먼저 고친다.
-- 진행 중인 스테이지 플레이의 체크포인트를 덮어쓴다. 웨이브가 뒤로 가는 저장만 막는다(같은 웨이브는 덮어씀).
-- 비교와 저장을 한 번에 해서, 늦게 도착한 앞 웨이브의 저장이 뒤 웨이브를 덮지 못하게 한다.
-- KEYS[1] = pw01:play-checkpoint:<stagePlayId>
-- ARGV[1] = 새 clearedWave(정수), ARGV[2] = 저장할 JSON 문자열, ARGV[3] = 만료(초)
-- 돌려줌: 1 = 저장함, -1 = 저장된 clearedWave보다 작음(저장 안 함)
local current = redis.call('GET', KEYS[1])
if current then
  local saved = cjson.decode(current)
  if tonumber(saved.clearedWave) > tonumber(ARGV[1]) then
    return -1
  end
end
redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
return 1
