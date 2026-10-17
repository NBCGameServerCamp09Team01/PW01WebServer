package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 길드 설정 값(pw01.guild.*). 기본값은 application.properties에 있다.
 * 값의 뜻은 루트 docs/contracts/guild-api.md "규칙"이 기준이다.
 * 만들기 조건 스테이지가 마스터 데이터에 있는지는 GuildConfig가 뜰 때 확인한다.
 *
 * @param maxMembers          정원. 길드를 만들 때 guild.max_members 칼럼에 적는다(이미 있는 길드는 바뀌지 않음)
 * @param maxGuilds           서버 전체 길드 수 상한(guild_counter.guild_count와 비교)
 * @param createRequiredStage 길드를 만들려면 클리어해야 하는 스테이지 ID
 * @param maxPendingRequests  한 사람의 대기 중 가입 신청 수 상한
 * @param joinRequestTtl      가입 신청 유효 기간(guild_join_request.created_at부터)
 * @param rejoinCooldown      탈퇴·추방 뒤 재가입 대기 시간(Lv2). 0이면 끔
 * @param list                길드 목록(G1) 크기
 */
@ConfigurationProperties("pw01.guild")
public record GuildProperties(int maxMembers, int maxGuilds, String createRequiredStage, int maxPendingRequests,
                              Duration joinRequestTtl, Duration rejoinCooldown, ListSize list) {

    public GuildProperties {
        if (maxMembers < 1) {
            throw new IllegalArgumentException("pw01.guild.max-members는 1 이상이어야 합니다: " + maxMembers);
        }
        if (maxGuilds < 1) {
            throw new IllegalArgumentException("pw01.guild.max-guilds는 1 이상이어야 합니다: " + maxGuilds);
        }
        if (createRequiredStage == null || createRequiredStage.isBlank()) {
            throw new IllegalArgumentException("pw01.guild.create-required-stage가 비어 있습니다");
        }
        if (maxPendingRequests < 1) {
            throw new IllegalArgumentException("pw01.guild.max-pending-requests는 1 이상이어야 합니다: " + maxPendingRequests);
        }
        if (joinRequestTtl == null || joinRequestTtl.isNegative() || joinRequestTtl.isZero()) {
            throw new IllegalArgumentException("pw01.guild.join-request-ttl은 0보다 커야 합니다: " + joinRequestTtl);
        }
        if (rejoinCooldown == null || rejoinCooldown.isNegative()) {
            throw new IllegalArgumentException("pw01.guild.rejoin-cooldown은 0 이상이어야 합니다: " + rejoinCooldown);
        }
        if (list == null) {
            throw new IllegalArgumentException("pw01.guild.list.*가 비어 있습니다");
        }
    }

    /**
     * @param defaultSize size를 안 보냈을 때
     * @param maxSize     size 최댓값(넘으면 400)
     */
    public record ListSize(int defaultSize, int maxSize) {

        public ListSize {
            if (defaultSize < 1 || maxSize < defaultSize) {
                throw new IllegalArgumentException(
                        "pw01.guild.list는 1 ≤ default-size ≤ max-size여야 합니다: " + defaultSize + ", " + maxSize);
            }
        }

    }

}
