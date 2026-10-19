package com.pw01.webserver.stat.service;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stat.master.StatsFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * master/stats.json 기동 검사(단위). 틀린 파일이면 서버가 뜨지 않아야 한다.
 * 실패하면 StatCatalog.validate의 검사 항목(빈 목록·빈 태그·태그 길이·maxPoints·중복)을 의심한다.
 */
class StatCatalogTest {

    // 확인: 맞는 파일이면 태그로 최대 포인트를 찾고, 없는 태그는 빈 값
    @Test
    void 태그로_최대_포인트를_찾는다() {
        StatCatalog catalog = new StatCatalog(new StatsFile(List.of(stat("Account.Stat.MaxHealth", 10))));

        assertThat(catalog.maxPoints("Account.Stat.MaxHealth")).contains(10);
        assertThat(catalog.maxPoints("Account.Stat.Luck")).isEmpty();
    }

    // 확인: 빈 목록·빈 태그·28자 태그·maxPoints 0·중복 태그는 기동 실패
    @Test
    void 틀린_파일은_기동_실패() {
        assertInvalid(new StatsFile(List.of()));
        assertInvalid(new StatsFile(List.of(stat(" ", 10))));
        assertInvalid(new StatsFile(List.of(stat("Account.Stat.ABCDEFGHIJKLMNO", 10))));
        assertInvalid(new StatsFile(List.of(stat("Account.Stat.MaxHealth", 0))));
        assertInvalid(new StatsFile(List.of(stat("Account.Stat.MaxHealth", 10), stat("Account.Stat.MaxHealth", 5))));
    }

    // 확인: 27자 태그는 받는다(변경 내역 source_id 64자 = requestId 36 + ":" + 27)
    @Test
    void 태그는_27자까지() {
        String tag = "Account.Stat.ABCDEFGHIJKLMN";
        assertThat(tag).hasSize(StatCatalog.TAG_MAX_LENGTH);
        assertThat(new StatCatalog(new StatsFile(List.of(stat(tag, 1)))).maxPoints(tag)).contains(1);
    }

    private static void assertInvalid(StatsFile file) {
        assertThatThrownBy(() -> new StatCatalog(file)).isInstanceOf(MasterDataException.class);
    }

    private static StatsFile.Stat stat(String tag, int maxPoints) {
        return new StatsFile.Stat(tag, maxPoints, 1.0);
    }

}
