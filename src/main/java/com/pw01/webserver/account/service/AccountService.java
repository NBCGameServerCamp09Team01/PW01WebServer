package com.pw01.webserver.account.service;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.dto.LoginCandidate;
import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.entity.AccountLedger;
import com.pw01.webserver.account.entity.AccountProgress;
import com.pw01.webserver.account.repository.AccountLedgerRepository;
import com.pw01.webserver.account.repository.AccountProgressRepository;
import com.pw01.webserver.account.repository.AccountRepository;
import com.pw01.webserver.account.repository.AccountStatTotalRepository;
import com.pw01.webserver.common.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    static final String LOGIN_ID_DUPLICATED = "ACCOUNT_LOGIN_ID_DUPLICATED";
    static final String NICKNAME_DUPLICATED = "ACCOUNT_NICKNAME_DUPLICATED";
    /** 누적 통계 키(account_stat_total). S4 해금 조건 재료 */
    public static final String STAT_RUN_PLAYED = "run.played";
    public static final String STAT_RUN_CLEARED = "run.cleared";
    public static final String STAT_RUN_KILLS = "run.kills";
    public static final String STAT_RUN_GOLD = "run.gold";
    private static final String UK_LOGIN_ID = "uk_account_login_id";
    private static final String UK_NICKNAME = "uk_account_nickname";

    private final AccountRepository accountRepository;
    private final AccountProgressRepository accountProgressRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final AccountStatTotalRepository accountStatTotalRepository;
    private final LevelCurve levelCurve;

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

    /** 메인화면 값. 계정은 있는데 진행 행이 없으면 데이터 버그라 500으로 둔다 */
    public AccountSnapshotResponse getSnapshot(Long accountId) {
        return accountProgressRepository.findById(accountId)
                .map(AccountSnapshotResponse::from)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
    }

    /**
     * 결과 보상(S2): 경험치를 더해 레벨·스탯 포인트를 다시 계산하고, 변경 내역 한 줄과 누적 통계를 같이 남긴다.
     * 결과 트랜잭션 안에서만 부른다(MANDATORY): 결과·보상·진행이 따로 커밋되어 어긋나지 않게.
     * 진행 행은 @Version으로 지킨다. 같은 계정의 다른 결과와 부딪히면 낙관적 락 예외가 나고, 부른 쪽이 트랜잭션을 새로 다시 한다.
     * 스냅샷은 저장(flush)한 뒤에 만들어 올라간 version을 담는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StageRewardResult grantStageReward(Long accountId, String runId, String requestId, boolean cleared,
                                              int killCount, int earnedGold) {
        AccountProgress progress = accountProgressRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("account_progress가 없습니다. accountId=" + accountId));
        LevelGain gain = levelCurve.gain(progress.getLevel(), progress.getTotalExperience(),
                levelCurve.resultExp(cleared));
        progress.apply(gain);
        accountProgressRepository.saveAndFlush(progress);

        accountLedgerRepository.save(AccountLedger.stageReward(accountId, runId, requestId,
                gain.expGained(), gain.statPointsGained()));

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put(STAT_RUN_PLAYED, 1L);
        stats.put(STAT_RUN_CLEARED, cleared ? 1L : 0L);
        stats.put(STAT_RUN_KILLS, (long) killCount);
        stats.put(STAT_RUN_GOLD, (long) earnedGold);
        accountStatTotalRepository.add(accountId, stats);

        return new StageRewardResult(gain, AccountSnapshotResponse.from(progress));
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
