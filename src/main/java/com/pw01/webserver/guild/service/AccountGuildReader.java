package com.pw01.webserver.guild.service;

import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * account_guild 행을 읽는 한 곳. 쓰기 트랜잭션에서는 행이 없으면 그 자리에서 무소속 account_guild 행을 만든다(P-9).
 * 행이 없는 경우: V10 뒤 옛 서버가 받은 가입(섞여 도는 배포), 시험에서 SQL로 만든 계정 등.
 * insertIfAbsent는 동시에 두 번 불려도 예외 없이 행 하나로 끝난다. 다만 늦은 쪽은 먼저 넣은 쪽의 행을
 * 보통 읽기로는 못 본다(REPEATABLE READ 스냅숏) → 넣은 뒤에는 잠금 읽기(가장 최근 커밋된 행)로 읽는다.
 */
@Service
@RequiredArgsConstructor
public class AccountGuildReader {

    private final AccountGuildRepository accountGuildRepository;

    /** 읽기 전용(조회 API). 행이 없으면 비어 있고, 부르는 쪽은 무소속으로 본다(읽기 트랜잭션에서는 넣지 않음) */
    @Transactional(readOnly = true)
    public Optional<AccountGuild> find(Long accountId) {
        return accountGuildRepository.findById(accountId);
    }

    /** 쓰기 트랜잭션 안에서: 내 account_guild 행(없으면 무소속으로 만들어서). 낙관적 락(@Version)은 저장 때 확인된다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountGuild getOrCreate(Long accountId) {
        return accountGuildRepository.findById(accountId).orElseGet(() -> {
            accountGuildRepository.insertIfAbsent(accountId, Instant.now());
            return accountGuildRepository.findLockedById(accountId).orElseThrow();
        });
    }

    /**
     * 신청 트랜잭션 안에서: 내 account_guild 행을 잡으며 version을 곧바로 올린다(PESSIMISTIC_FORCE_INCREMENT, 없으면 먼저 만듦).
     * 같은 사람의 동시 신청이 여기서 차례로 선다(I-6). **트랜잭션의 첫 SQL이어야 한다.**
     * <ul>
     *   <li>insertIfAbsent(쓰기)를 늘 먼저 실행한다. 행이 있으면 아무것도 바꾸지 않지만 그 행에 배타 잠금을 건다.
     *       없는 행을 먼저 잠금 읽기하면 갭 락이 걸려, 동시에 넣는 둘이 서로의 삽입을 기다리는 교착이 생길 수 있다.</li>
     *   <li>일반 SELECT(existsById 등)를 잠금보다 먼저 실행하지 않는다. REPEATABLE READ의 스냅숏은 첫 일반 SELECT에서
     *       고정되므로, 잠금을 기다리기 전에 스냅숏이 잡히면 앞선 신청이 커밋한 행을 뒤의 개수 세기가 보지 못한다
     *       ("대기 중 3건" 판정이 깨짐). 쓰기·잠금 읽기는 스냅숏을 고정하지 않는다.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountGuild getOrCreateForApply(Long accountId) {
        accountGuildRepository.insertIfAbsent(accountId, Instant.now());
        return accountGuildRepository.findForApplyById(accountId).orElseThrow();
    }

}
