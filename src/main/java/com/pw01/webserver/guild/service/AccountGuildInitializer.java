package com.pw01.webserver.guild.service;

import com.pw01.webserver.account.event.AccountCreatedEvent;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 가입 때 무소속 account_guild 행(account_guild)을 만든다(G-40 a). 가입 트랜잭션 안에서 불리므로(@EventListener) 이 행을 못 만들면 가입도 되돌아간다.
 * 계정 기능은 이 클래스를 모른다(이벤트만 냄). 이 이벤트 전에 만들어진 계정은 V10이 채웠고,
 * 그래도 행이 없는 계정(섞여 도는 배포에서 옛 서버가 받은 가입 등)은 AccountGuildReader가 그 자리에서 만든다.
 */
@Component
@RequiredArgsConstructor
public class AccountGuildInitializer {

    private final AccountGuildRepository accountGuildRepository;

    @EventListener
    public void onAccountCreated(AccountCreatedEvent event) {
        accountGuildRepository.save(AccountGuild.unaffiliated(event.accountId()));
    }

}
