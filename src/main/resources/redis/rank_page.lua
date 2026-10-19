-- 순위 목록 한 묶음. 커서(마지막으로 준 줄의 점수·멤버) 다음부터 읽는다.
-- KEYS[1] = pw01:rank:<보드>, KEYS[2] = pw01:rank:<보드>:member
-- ARGV[1] = 읽을 줄 수(limit + 1, 한 줄 더 읽어 다음이 있는지 안다)
-- ARGV[2] = 커서 줄의 ZSet 점수(없으면 빈 문자열), ARGV[3] = 커서 줄의 멤버
-- 돌려줌: 보드가 없으면 nil / {시작 위치(0부터), {멤버, 점수, 멤버, 점수, …}}
if redis.call('HEXISTS', KEYS[2], '@') == 0 then
  return false
end
local start = 0
if ARGV[2] ~= '' then
  local s = redis.call('ZSCORE', KEYS[1], ARGV[3])
  if s and tonumber(s) == tonumber(ARGV[2]) then
    start = redis.call('ZRANK', KEYS[1], ARGV[3]) + 1
  else
    -- 커서 줄이 그사이 바뀌었으면(그 계정의 기록이 올라 멤버가 바뀜) 값으로 위치를 센다
    start = redis.call('ZCOUNT', KEYS[1], '-inf', '(' .. ARGV[2])
    local same = redis.call('ZRANGE', KEYS[1], ARGV[2], ARGV[2], 'BYSCORE')
    for _, m in ipairs(same) do
      if m <= ARGV[3] then
        start = start + 1
      end
    end
  end
end
local rows = redis.call('ZRANGE', KEYS[1], start, start + tonumber(ARGV[1]) - 1, 'WITHSCORES')
return {start, rows}
