package com.pw01.webserver.stat.service;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stat.master.StatsFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 투자할 수 있는 스탯과 최대 포인트: master/stats.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * 스탯을 더할 때는 파일 한 줄(+ UE 한 줄)이면 되고 이 클래스는 바꾸지 않는다. 값은 기동 뒤 바뀌지 않는다.
 */
@Component
public class StatCatalog {

    static final String FILE_NAME = "stats.json";
    /**
     * 태그 최대 길이. 변경 내역 source_id(VARCHAR 64) = requestId 36자 + ":" + 태그이므로 27자까지(AccountLedger.statChange)
     */
    static final int TAG_MAX_LENGTH = 27;

    private final Map<String, Integer> maxPointsByTag;

    @Autowired
    public StatCatalog(MasterDataLoader loader) {
        this(loader.load(FILE_NAME, StatsFile.class));
    }

    /** 시험용: 파일 없이 값으로 만든다(검사는 같다) */
    StatCatalog(StatsFile file) {
        this.maxPointsByTag = validate(file);
    }

    /** 스탯의 최대 포인트. 없는 스탯이면 빈 값 */
    public Optional<Integer> maxPoints(String tag) {
        return Optional.ofNullable(maxPointsByTag.get(tag));
    }

    private static Map<String, Integer> validate(StatsFile file) {
        if (file.stats() == null || file.stats().isEmpty()) {
            throw fail("stats가 비어 있습니다.");
        }
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StatsFile.Stat stat : file.stats()) {
            if (stat.tag() == null || stat.tag().isBlank()) {
                throw fail("tag가 비어 있는 스탯이 있습니다.");
            }
            if (stat.tag().length() > TAG_MAX_LENGTH) {
                throw fail("tag는 " + TAG_MAX_LENGTH + "자 이하여야 합니다(변경 내역 source_id 길이): " + stat.tag());
            }
            if (stat.maxPoints() < 1) {
                throw fail("maxPoints는 1 이상이어야 합니다: " + stat.tag());
            }
            if (map.put(stat.tag(), stat.maxPoints()) != null) {
                throw fail("tag가 겹칩니다: " + stat.tag());
            }
        }
        return Map.copyOf(map);
    }

    private static MasterDataException fail(String message) {
        return new MasterDataException(FILE_NAME, message);
    }

}
