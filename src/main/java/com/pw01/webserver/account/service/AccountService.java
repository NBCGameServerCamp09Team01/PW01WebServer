package com.pw01.webserver.account.service;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.dto.LoginCandidate;
import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.entity.AccountLedger;
import com.pw01.webserver.account.entity.AccountProgress;
import com.pw01.webserver.account.entity.AccountSkill;
import com.pw01.webserver.account.entity.AccountSkillId;
import com.pw01.webserver.account.entity.AccountStat;
import com.pw01.webserver.account.repository.AccountLedgerRepository;
import com.pw01.webserver.account.repository.AccountProgressRepository;
import com.pw01.webserver.account.repository.AccountRepository;
import com.pw01.webserver.account.repository.AccountSkillRepository;
import com.pw01.webserver.account.repository.AccountStatRepository;
import com.pw01.webserver.account.repository.AccountStatTotalRepository;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.progression.requirement.AccountFacts;
import com.pw01.webserver.progression.requirement.RequirementProgress;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    static final String LOGIN_ID_DUPLICATED = "ACCOUNT_LOGIN_ID_DUPLICATED";
    static final String NICKNAME_DUPLICATED = "ACCOUNT_NICKNAME_DUPLICATED";
    /** 누적 통계 키(account_stat_total). S4 해금 조건 재료 */
    public static final String STAT_STAGE_PLAY_PLAYED = "stage_play.played";
    public static final String STAT_STAGE_PLAY_CLEARED = "stage_play.cleared";
    public static final String STAT_STAGE_PLAY_KILLS = "stage_play.kills";
    public static final String STAT_STAGE_PLAY_GOLD = "stage_play.gold";
    /** S4: 플레이 시간 합계(밀리초), 가장 멀리 간 웨이브(최댓값), 가장 빠른 클리어(밀리초, 최솟값·클리어만) */
    public static final String STAT_STAGE_PLAY_PLAY_TIME_MS = "stage_play.play_time_ms";
    public static final String STAT_STAGE_PLAY_MAX_WAVE = "stage_play.max_wave";
    public static final String STAT_STAGE_PLAY_BEST_CLEAR_MS = "stage_play.best_clear_ms";
    private static final String UK_LOGIN_ID = "uk_account_login_id";
    private static final String UK_NICKNAME = "uk_account_nickname";

    private final AccountRepository accountRepository;
    private final AccountProgressRepository accountProgressRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final AccountStatTotalRepository accountStatTotalRepository;
    private final AccountStatRepository accountStatRepository;
    private final AccountSkillRepository accountSkillRepository;
    private final LevelCurve levelCurve;
    private final EntityManager entityManager;

    @Transactional
    public Account register(String loginId, String passwordHash, String nickname, String email){

        // 1차 중복 확인
        if(accountRepository.existsByLoginId(loginId)){
            throw loginIdDuplicated();
        }
        if(accountRepository.existsByNickname(nickname)){
            throw nicknameDuplicated();
        }
        try{
            Account account = accountRepository.saveAndFlush(
                    Account.create(loginId, passwordHash, nickname, email));
            accountProgressRepository.save(AccountProgress.initial(account.getId()));
            return account;
        } catch (DataIntegrityViolationException e){ // 2차 방어(동시 가입을 DB가 막은 경우)
            throw toConflict(e);
        }
    }

    /** 로그인 판단용 값(아이디는 대소문자까지 같은 것만 찾는다). 엔티티 대신 필요한 값만 돌려준다 */
    public Optional<LoginCandidate> findLoginCandidate(String loginId) {
        return accountRepository.findByLoginId(loginId)
                .map(account -> new LoginCandidate(account.getId(), account.getPasswordHash(), account.getStatus()));
    }

    /** 계정 ID → 닉네임. 다른 기능의 목록(예: 진행 중 스테이지 플레이)에 이름을 붙일 때 쓴다. 없는 ID는 빠진다 */
    public Map<Long, String> getNicknames(Collection<Long> accountIds) {
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getNickname));
    }

    /**
     * 메인화면 값(계정 스냅샷)의 한 입구. 로그인·A3·결과·투자·해금 응답이 모두 여기서 만든 값을 쓴다.
     * 나중에 계정 스냅샷 조립기(SF-8)를 만들면 이 메서드 안쪽(snapshot)만 바꾼다.
     * 계정은 있는데 진행 행이 없으면 데이터 버그라 500으로 둔다.
     */
    public AccountSnapshotResponse getSnapshot(Long accountId) {
        return accountProgressRepository.findById(accountId)
                .map(this::snapshot)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
    }

    /**
     * 스탯 투자(S3): 묶음 전체를 판정하고, 되면 포인트·스탯·변경 내역을 저장한다. 하나라도 걸리면 아무것도 저장하지 않는다.
     * 트랜잭션은 부른 쪽(ProgressWriter)이 연다(MANDATORY). 진행 행 @Version 충돌이면 부른 쪽이 새 트랜잭션으로 다시 한다.
     * 이 requestId의 내역이 이미 있으면 ALREADY_SAVED(아무것도 바꾸지 않음). 동시에 같은 요청이 와도 내역 유일 제약이 한 번만 남긴다.
     * 포인트가 그대로인 묶음(+1·−1 등)도 진행 행 version을 올린다(지시서 3장 "자식 표만 바뀌어도 진행 행 버전을 올린다").
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StatChangeResult changeStats(Long accountId, String requestId, List<StatChange> changes) {
        if (accountLedgerRepository.existsByAccountIdAndRequestId(accountId, requestId)) {
            return StatChangeResult.of(StatChangeResult.Outcome.ALREADY_SAVED);
        }
        AccountProgress progress = accountProgressRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
        Map<String, AccountStat> current = accountStatRepository.findByIdAccountId(accountId).stream()
                .collect(Collectors.toMap(AccountStat::getStatKey, stat -> stat));

        int need = changes.stream().mapToInt(StatChange::points).sum();
        if (need > progress.getStatPoints()) {
            return StatChangeResult.pointsNotEnough(need, progress.getStatPoints());
        }
        List<StatChangeResult.Violation> overMax = new ArrayList<>();
        List<StatChangeResult.Violation> belowZero = new ArrayList<>();
        for (StatChange change : changes) {
            int now = current.containsKey(change.statKey()) ? current.get(change.statKey()).getPoints() : 0;
            int after = now + change.points();
            if (after > change.maxPoints()) {
                overMax.add(new StatChangeResult.Violation(change, now));
            } else if (after < 0) {
                belowZero.add(new StatChangeResult.Violation(change, now));
            }
        }
        if (!overMax.isEmpty()) {
            return StatChangeResult.violations(StatChangeResult.Outcome.MAX_EXCEEDED, overMax);
        }
        if (!belowZero.isEmpty()) {
            return StatChangeResult.violations(StatChangeResult.Outcome.BELOW_ZERO, belowZero);
        }

        progress.changeStatPoints(-need);
        if (need == 0) {
            entityManager.lock(progress, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        for (StatChange change : changes) {
            AccountStat stat = current.computeIfAbsent(change.statKey(), key -> AccountStat.empty(accountId, key));
            stat.add(change.points());
            accountStatRepository.save(stat);
            accountLedgerRepository.save(AccountLedger.statChange(accountId, requestId, change.statKey(), change.points()));
        }
        // 내역 유일 제약·진행 행 version 충돌을 여기서 드러낸다(부른 쪽이 다시 할지 정함)
        accountProgressRepository.saveAndFlush(progress);
        return StatChangeResult.of(StatChangeResult.Outcome.SAVED);
    }

    /**
     * 조건 판정에 쓰는 계정 값(레벨·포인트·누적 통계·해금 스킬). 스킬 진행도 조회(K2)와 해금 판정이 같이 쓴다.
     * 트랜잭션 안에서 부르면 그 트랜잭션의 값을 읽는다(해금은 ProgressWriter 트랜잭션 안에서 읽고 바로 판정한다).
     */
    public AccountFacts facts(Long accountId) {
        return facts(progress(accountId));
    }

    /**
     * 스킬 해금(S4): 이미 해금했는지 → 조건 판정(judge) → 되면 포인트·해금·변경 내역을 저장한다. 걸리면 아무것도 저장하지 않는다.
     * 트랜잭션은 부른 쪽(ProgressWriter)이 연다(MANDATORY). 진행 행 @Version 충돌이나 같은 스킬 동시 해금(기본 키 위반)이면
     * 부른 쪽이 새 트랜잭션으로 다시 하고, 그때는 첫 줄에서 이미 해금으로 걸린다.
     * judge는 스킬 정의를 아는 쪽(SkillJudge)이 넘긴다: 이 트랜잭션에서 읽은 계정 값으로 조건 전부를 판정한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SkillUnlockResult unlockSkill(Long accountId, String requestId, String skillTag, int cost,
                                         Function<AccountFacts, List<RequirementProgress>> judge) {
        Optional<AccountSkill> existing = accountSkillRepository.findById(new AccountSkillId(accountId, skillTag));
        if (existing.isPresent()) {
            return SkillUnlockResult.of(existing.get().getRequestId().equals(requestId)
                    ? SkillUnlockResult.Outcome.ALREADY_SAVED
                    : SkillUnlockResult.Outcome.ALREADY_UNLOCKED);
        }
        AccountProgress progress = progress(accountId);
        List<RequirementProgress> requirements = judge.apply(facts(progress));
        if (requirements.stream().anyMatch(requirement -> !requirement.met())) {
            return SkillUnlockResult.notMet(requirements);
        }

        progress.changeStatPoints(-cost);
        if (cost == 0) {
            entityManager.lock(progress, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        accountSkillRepository.save(AccountSkill.unlocked(accountId, skillTag, requestId));
        accountLedgerRepository.save(AccountLedger.skillUnlock(accountId, requestId, skillTag, cost));
        // 기본 키(같은 스킬 동시 해금)·내역 유일 제약·진행 행 version 충돌을 여기서 드러낸다(부른 쪽이 다시 할지 정함)
        accountProgressRepository.saveAndFlush(progress);
        return SkillUnlockResult.of(SkillUnlockResult.Outcome.SAVED);
    }

    /**
     * 결과 보상(S2): 경험치를 더해 레벨·스탯 포인트를 다시 계산하고, 변경 내역 한 줄과 누적 통계를 같이 남긴다.
     * 누적 통계: 합계 5개(플레이·클리어·처치·골드·플레이 시간)는 더하고, 최고 기록 2개(웨이브·클리어 시간)는 최댓값·최솟값으로(S4).
     * 결과 트랜잭션 안에서만 부른다(MANDATORY): 결과·보상·진행이 따로 커밋되어 어긋나지 않게.
     * 진행 행은 @Version으로 지킨다. 같은 계정의 다른 결과와 부딪히면 낙관적 락 예외가 나고, 부른 쪽이 트랜잭션을 새로 다시 한다.
     * 스냅샷은 저장(flush)한 뒤에 만들어 올라간 version을 담는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StageRewardResult grantStageReward(Long accountId, String stagePlayId, String requestId, boolean cleared,
                                              int killCount, int earnedGold, int reachedWave, long playTimeMs) {
        AccountProgress progress = accountProgressRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
        LevelGain gain = levelCurve.gain(progress.getLevel(), progress.getTotalExperience(),
                levelCurve.resultExp(cleared));
        progress.apply(gain);
        accountProgressRepository.saveAndFlush(progress);

        accountLedgerRepository.save(AccountLedger.stageReward(accountId, stagePlayId, requestId,
                gain.expGained(), gain.statPointsGained()));

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put(STAT_STAGE_PLAY_PLAYED, 1L);
        stats.put(STAT_STAGE_PLAY_CLEARED, cleared ? 1L : 0L);
        stats.put(STAT_STAGE_PLAY_KILLS, (long) killCount);
        stats.put(STAT_STAGE_PLAY_GOLD, (long) earnedGold);
        stats.put(STAT_STAGE_PLAY_PLAY_TIME_MS, playTimeMs);
        accountStatTotalRepository.add(accountId, stats);
        // 최고 기록(S4 해금 조건 재료): 가장 멀리 간 웨이브, 가장 빠른 클리어(클리어만)
        accountStatTotalRepository.max(accountId, STAT_STAGE_PLAY_MAX_WAVE, reachedWave);
        if (cleared) {
            accountStatTotalRepository.min(accountId, STAT_STAGE_PLAY_BEST_CLEAR_MS, playTimeMs);
        }

        return new StageRewardResult(gain, snapshot(progress));
    }

    /** 스냅샷 조립은 여기 한 곳. 투자 값은 0보다 큰 스탯만(account-api.md v1.1), 해금 스킬은 태그 이름순(v1.2) */
    private AccountSnapshotResponse snapshot(AccountProgress progress) {
        Map<String, Integer> invested = new TreeMap<>();
        for (AccountStat stat : accountStatRepository.findByIdAccountId(progress.getAccountId())) {
            if (stat.getPoints() > 0) {
                invested.put(stat.getStatKey(), stat.getPoints());
            }
        }
        return AccountSnapshotResponse.from(progress, invested, List.copyOf(unlockedSkills(progress.getAccountId())));
    }

    private AccountFacts facts(AccountProgress progress) {
        Long accountId = progress.getAccountId();
        return new AccountFacts(progress.getVersion(), progress.getLevel(), progress.getStatPoints(),
                accountStatTotalRepository.totals(accountId), unlockedSkills(accountId));
    }

    /** 해금한 스킬 태그(이름순) */
    private TreeSet<String> unlockedSkills(Long accountId) {
        return accountSkillRepository.findByIdAccountId(accountId).stream()
                .map(AccountSkill::getSkillTag)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private AccountProgress progress(Long accountId) {
        return accountProgressRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
    }

    private static RuntimeException toConflict(DataIntegrityViolationException e) {
        if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve
                && cve.getConstraintName() != null) {
            String name = cve.getConstraintName();
            if (name.endsWith(UK_LOGIN_ID)) {
                return loginIdDuplicated();
            }
            if (name.endsWith(UK_NICKNAME)) {
                return nicknameDuplicated();
            }
        }
        return e;
    }

    private static ConflictException loginIdDuplicated(){
        return new ConflictException(LOGIN_ID_DUPLICATED, "이미 사용 중인 아이디입니다.");
    }

    private static ConflictException nicknameDuplicated(){
        return new ConflictException(NICKNAME_DUPLICATED, "이미 사용 중인 닉네임입니다.");
    }
}
