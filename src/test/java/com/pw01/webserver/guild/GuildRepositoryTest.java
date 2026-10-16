package com.pw01.webserver.guild;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.entity.Guild;
import com.pw01.webserver.guild.entity.GuildJoinRequest;
import com.pw01.webserver.guild.entity.GuildRole;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import com.pw01.webserver.guild.repository.GuildCounterRepository;
import com.pw01.webserver.guild.repository.GuildJoinRequestRepository;
import com.pw01.webserver.guild.repository.GuildRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 길드 엔티티 넷이 V10 표와 맞는지(저장 → 다시 읽기), 저장소의 잠금·벌크·insertIfAbsent 쿼리가 뜻대로 도는지 본다.
 * 기동 자체(ddl-auto=validate)가 엔티티 칸 = 마이그레이션 칸의 첫 확인이다.
 * 실패하면 엔티티의 칼럼 이름·길이·enum 저장 방식, 저장소 쿼리(JPQL의 감사 칸 updatedAt, 네이티브 INSERT 칸 순서)를 먼저 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 계정 아이디·닉네임·길드 이름을 서로 다르게 쓴다.
 */
@IntegrationTest
class GuildRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    GuildRepository guilds;

    @Autowired
    AccountGuildRepository people;

    @Autowired
    GuildJoinRequestRepository requests;

    @Autowired
    GuildCounterRepository counters;

    // 확인: 길드·사람 줄을 엔티티로 저장하고 같은 값으로 읽는다. 처음 version은 0, 이름 유무는 대소문자까지
    @Test
    void 길드와_사람_줄_저장_읽기() {
        long leader = insertAccount("GuildRp01", "길드저장01");
        Guild saved = guilds.save(Guild.create("저장Dawn", leader, 30));
        AccountGuild person = AccountGuild.unaffiliated(leader);
        person.join(saved.getId(), GuildRole.LEADER, NOW);
        people.save(person);

        Guild guild = guilds.findById(saved.getId()).orElseThrow();
        assertThat(guild.getName()).isEqualTo("저장Dawn");
        assertThat(guild.getMemberCount()).isEqualTo(1);
        assertThat(guild.getVersion()).isZero();
        assertThat(guilds.existsByName("저장Dawn")).isTrue();
        assertThat(guilds.existsByName("저장dawn")).isFalse();

        AccountGuild read = people.findById(leader).orElseThrow();
        assertThat(read.getGuildId()).isEqualTo(saved.getId());
        assertThat(read.getRole()).isEqualTo(GuildRole.LEADER);
        assertThat(read.getJoinedAt()).isEqualTo(NOW);
        assertThat(people.findAccountIdsByGuildId(saved.getId())).containsExactly(leader);
    }

    // 확인: 인원을 바꿔 저장하면 version이 오른다(낙관적 락이 걸리는 칸)
    @Test
    void 인원이_바뀌면_version이_오름() {
        long leader = insertAccount("GuildRp02", "길드저장02");
        Long guildId = guilds.save(Guild.create("버전길드", leader, 30)).getId();

        tx.executeWithoutResult(s -> guilds.findById(guildId).orElseThrow().increaseMemberCount());

        Guild guild = guilds.findById(guildId).orElseThrow();
        assertThat(guild.getMemberCount()).isEqualTo(2);
        assertThat(guild.getVersion()).isEqualTo(1L);
    }

    // 확인: insertIfAbsent는 두 번 불러도 줄 하나, 이미 소속인 줄은 건드리지 않는다(예외 없음)
    @Test
    void 무소속_줄은_없을_때만() {
        long account = insertAccount("GuildRp03", "길드저장03");
        long member = insertAccount("GuildRp04", "길드저장04");
        Long guildId = guilds.save(Guild.create("넣기길드", member, 30)).getId();
        AccountGuild joined = AccountGuild.unaffiliated(member);
        joined.join(guildId, GuildRole.LEADER, NOW);
        people.save(joined);

        tx.executeWithoutResult(s -> {
            people.insertIfAbsent(account, NOW);
            people.insertIfAbsent(account, NOW);
            people.insertIfAbsent(member, NOW);
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_guild WHERE account_id = ?", Integer.class, account))
                .isEqualTo(1);
        assertThat(people.findById(account).orElseThrow().isInGuild()).isFalse();
        assertThat(people.findById(member).orElseThrow().belongsTo(guildId)).isTrue();
    }

    // 확인: 신청용 잠금은 읽는 순간 사람 줄 version을 올린다(그 트랜잭션에서 다른 칸을 안 바꿔도)
    @Test
    void 신청용_잠금은_version을_올림() {
        long account = insertAccount("GuildRp05", "길드저장05");
        people.save(AccountGuild.unaffiliated(account));
        long before = people.findById(account).orElseThrow().getVersion();

        tx.executeWithoutResult(s -> assertThat(people.findForApplyById(account)).isPresent());

        assertThat(people.findById(account).orElseThrow().getVersion()).isEqualTo(before + 1);
    }

    // 확인: 해체용 잠금으로 길드 줄을 읽을 수 있다(FOR UPDATE는 트랜잭션 안에서만)
    @Test
    void 해체용_잠금으로_읽기() {
        long leader = insertAccount("GuildRp06", "길드저장06");
        Long guildId = guilds.save(Guild.create("잠금길드", leader, 30)).getId();

        Guild locked = tx.execute(s -> guilds.findByIdForUpdate(guildId).orElseThrow());

        assertThat(locked.getName()).isEqualTo("잠금길드");
    }

    // 확인: 해체 벌크는 그 길드원만 무소속으로 하고 version을 올린다. 다른 길드는 그대로
    @Test
    void 해체_벌크는_그_길드만() {
        long a = insertAccount("GuildRp07", "길드저장07");
        long b = insertAccount("GuildRp08", "길드저장08");
        long c = insertAccount("GuildRp09", "길드저장09");
        Long target = guilds.save(Guild.create("해체대상", a, 30)).getId();
        Long other = guilds.save(Guild.create("남는길드", c, 30)).getId();
        saveMember(a, target, GuildRole.LEADER);
        saveMember(b, target, GuildRole.MEMBER);
        saveMember(c, other, GuildRole.LEADER);

        Integer cleared = tx.execute(s -> people.clearGuild(target, NOW));

        assertThat(cleared).isEqualTo(2);
        AccountGuild ra = people.findById(a).orElseThrow();
        assertThat(ra.isInGuild()).isFalse();
        assertThat(ra.getRole()).isNull();
        assertThat(ra.getJoinedAt()).isNull();
        assertThat(ra.getVersion()).isEqualTo(1L);
        assertThat(people.findById(b).orElseThrow().isInGuild()).isFalse();
        assertThat(people.findById(c).orElseThrow().belongsTo(other)).isTrue();
    }

    // 확인: 신청 저장 → 신청 시각이 채워지고 유효 기간을 계산한다, 사람별·길드별 일괄 지우기는 자기 것만
    @Test
    void 신청_저장과_일괄_지우기() {
        long leader1 = insertAccount("GuildRp10", "길드저장10");
        long leader2 = insertAccount("GuildRp11", "길드저장11");
        long applicant = insertAccount("GuildRp12", "길드저장12");
        long other = insertAccount("GuildRp13", "길드저장13");
        Long g1 = guilds.save(Guild.create("신청길드1", leader1, 30)).getId();
        Long g2 = guilds.save(Guild.create("신청길드2", leader2, 30)).getId();
        GuildJoinRequest first = requests.save(GuildJoinRequest.application(g1, applicant));
        requests.save(GuildJoinRequest.application(g2, applicant));
        requests.save(GuildJoinRequest.application(g1, other));

        GuildJoinRequest read = requests.findByIdAndGuildId(first.getId(), g1).orElseThrow();
        assertThat(read.requestedAt()).isNotNull();
        assertThat(read.expiresAt(Duration.ofDays(3))).isEqualTo(read.requestedAt().plus(Duration.ofDays(3)));
        assertThat(requests.findByIdAndGuildId(first.getId(), g2)).isEmpty();
        assertThat(requests.findAllByAccountIdOrderByIdAsc(applicant)).hasSize(2);
        assertThat(requests.findAccountIdsByGuildId(g1)).containsExactlyInAnyOrder(applicant, other);

        Integer ofAccount = tx.execute(s -> requests.deleteAllOfAccount(applicant));
        assertThat(ofAccount).isEqualTo(2);
        assertThat(requests.findAllByGuildIdOrderByIdAsc(g1)).extracting(GuildJoinRequest::getAccountId)
                .containsExactly(other);

        Integer ofGuild = tx.execute(s -> requests.deleteAllOfGuild(g1));
        assertThat(ofGuild).isEqualTo(1);
        assertThat(requests.findAllByGuildIdOrderByIdAsc(g1)).isEmpty();
    }

    // 확인: 길드 수 줄 하나를 읽는다(V10이 만든 줄)
    @Test
    void 길드_수_줄_읽기() {
        assertThat(counters.getSingle().getGuildCount()).isGreaterThanOrEqualTo(0);
    }

    private void saveMember(long accountId, Long guildId, GuildRole role) {
        AccountGuild person = AccountGuild.unaffiliated(accountId);
        person.join(guildId, role, NOW);
        people.save(person);
    }

    private long insertAccount(String loginId, String nickname) {
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, nickname);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

}
