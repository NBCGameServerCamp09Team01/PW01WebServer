package com.pw01.webserver.account.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uk_account_nickname", columnNames = "nickname")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(nullable = false, length = 20)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    public static Account create(String loginId, String passwordHash, String nickname, String email){
        Account account = new Account();
        account.loginId = loginId;
        account.passwordHash = passwordHash;
        account.nickname = nickname;
        account.email = email;
        account.status = AccountStatus.ACTIVE;
        return account;
    }
}
