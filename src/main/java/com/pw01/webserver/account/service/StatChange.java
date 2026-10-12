package com.pw01.webserver.account.service;

/**
 * 스탯 하나의 바꿈(S3). points가 양수면 올림, 음수면 내림.
 *
 * @param index     요청 묶음 안의 순서(0부터). 거절할 때 errors 칸 이름(investments[i])에 쓴다
 * @param statKey   스탯 태그
 * @param points    0이 아닌 정수
 * @param maxPoints 그 스탯의 최대 포인트(마스터 데이터)
 */
public record StatChange(int index, String statKey, int points, int maxPoints) {
}
