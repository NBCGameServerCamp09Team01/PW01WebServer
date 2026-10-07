package com.pw01.webserver.account.service;

import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.entity.AccountProgress;
import com.pw01.webserver.account.repository.AccountProgressRepository;
import com.pw01.webserver.account.repository.AccountRepository;
import com.pw01.webserver.common.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    static final String LOGIN_ID_DUPLICATED = "ACCOUNT_LOGIN_ID_DUPLICATED";
    static final String NICKNAME_DUPLICATED = "ACCOUNT_NICKNAME_DUPLICATED";
    private static final String UK_LOGIN_ID = "uk_account_login_id";
    private static final String UK_NICKNAME = "uk_account_nickname";

    private final AccountRepository accountRepository;
    private final AccountProgressRepository accountProgressRepository;

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
