package com.pw01.webserver.guild.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 길드 엔티티의 규칙 메서드(DB 없이). 서비스가 먼저 판정해야 하는 경우를 엔티티가 IllegalStateException으로 막는지 본다.
 * 실패하면 엔티티 메서드의 경계 비교(>=, <=)를 먼저 의심한다.
 */
class GuildEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00.000Z");

    // 확인: 새 길드는 길드장 한 명(인원 1), 길드장 판별
    @Test
    void 새_길드는_길드장_한_명() {
        Guild guild = Guild.create("새벽기사단", 12L, 30);

        assertThat(guild.getMemberCount()).isEqualTo(1);
        assertThat(guild.getMaxMembers()).isEqualTo(30);
        assertThat(guild.isLeader(12L)).isTrue();
        assertThat(guild.isLeader(13L)).isFalse();
        assertThat(guild.isFull()).isFalse();
    }

    // 확인: 정원까지만 늘고, 찬 뒤에 늘리면 예외(서비스가 isFull로 먼저 거르는 것이 정상 길)
    @Test
    void 정원까지만_늘어남() {
        Guild guild = Guild.create("셋정원", 12L, 3);
        guild.increaseMemberCount();
        guild.increaseMemberCount();

        assertThat(guild.getMemberCount()).isEqualTo(3);
        assertThat(guild.isFull()).isTrue();
        assertThatThrownBy(guild::increaseMemberCount).isInstanceOf(IllegalStateException.class);
        assertThat(guild.getMemberCount()).isEqualTo(3);
    }

    // 확인: 길드장만 남으면 줄이지 않는다(혼자인 길드장 탈퇴는 해체 길)
    @Test
    void 길드장만_남으면_줄이지_않음() {
        Guild guild = Guild.create("둘길드", 12L, 30);
        guild.increaseMemberCount();
        guild.decreaseMemberCount();

        assertThat(guild.getMemberCount()).isEqualTo(1);
        assertThatThrownBy(guild::decreaseMemberCount).isInstanceOf(IllegalStateException.class);
    }

    // 확인: 정원 0 이하는 만들 수 없음(설정 값 실수)
    @Test
    void 정원은_1_이상() {
        assertThatThrownBy(() -> Guild.create("빈정원", 12L, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // 확인: 무소속 → 들어감 → 나감, 세 칼럼이 같이 바뀐다(DB CHECK ck_account_guild_membership와 같은 규칙)
    @Test
    void 소속_들어가고_나감() {
        AccountGuild person = AccountGuild.unaffiliated(57L);
        assertThat(person.isInGuild()).isFalse();

        person.join(7L, GuildRole.MEMBER, NOW);
        assertThat(person.isInGuild()).isTrue();
        assertThat(person.belongsTo(7L)).isTrue();
        assertThat(person.belongsTo(8L)).isFalse();
        assertThat(person.isLeader()).isFalse();
        assertThat(person.getJoinedAt()).isEqualTo(NOW);

        person.leave();
        assertThat(person.getGuildId()).isNull();
        assertThat(person.getRole()).isNull();
        assertThat(person.getJoinedAt()).isNull();
    }

    // 확인: 이미 길드가 있으면 다른 길드에 못 들고, 무소속이면 나갈 수 없다
    @Test
    void 한_사람_길드_하나() {
        AccountGuild person = AccountGuild.unaffiliated(57L);
        assertThatThrownBy(person::leave).isInstanceOf(IllegalStateException.class);

        person.join(7L, GuildRole.LEADER, NOW);
        assertThat(person.isLeader()).isTrue();
        assertThatThrownBy(() -> person.join(8L, GuildRole.MEMBER, NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(person.getGuildId()).isEqualTo(7L);
    }

    // 확인: 신청 유효 기간 = 신청 시각 + ttl, 끝 시각과 같으면 지난 것
    @Test
    void 신청_유효_기간() {
        GuildJoinRequest request = GuildJoinRequest.application(7L, 57L);
        ReflectionTestUtils.setField(request, "createdAt", NOW);
        Duration ttl = Duration.ofDays(3);

        assertThat(request.getKind()).isEqualTo(GuildJoinRequestKind.APPLICATION);
        assertThat(request.expiresAt(ttl)).isEqualTo(Instant.parse("2026-10-19T03:00:00.000Z"));
        assertThat(request.isExpired(NOW.plus(ttl).minusMillis(1), ttl)).isFalse();
        assertThat(request.isExpired(NOW.plus(ttl), ttl)).isTrue();
    }

    // 확인: 길드 수는 상한 미만일 때만 만들 수 있고, 0 아래로 내려가지 않는다
    @Test
    void 길드_수_상한() {
        GuildCounter counter = BeanUtils.instantiateClass(GuildCounter.class);
        assertThat(counter.canCreate(1)).isTrue();

        counter.increase();
        assertThat(counter.getGuildCount()).isEqualTo(1);
        assertThat(counter.canCreate(1)).isFalse();

        counter.decrease();
        assertThat(counter.getGuildCount()).isZero();
        assertThatThrownBy(counter::decrease).isInstanceOf(IllegalStateException.class);
    }

}
