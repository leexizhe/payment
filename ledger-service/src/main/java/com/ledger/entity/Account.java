package com.ledger.entity;

import com.ledger.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String currency;

    /** Balance in minor units (cents). Kept in sync with the sum of postings. */
    @Column(nullable = false)
    private long balanceMinor;

    @Version
    private long version;

    protected Account() {
    }

    public Account(String name, AccountType type, String currency, long balanceMinor) {
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
    }

    public boolean canDebit(long amountMinor) {
        // Treasury is the money supply; everyone else must stay non-negative.
        return type == AccountType.TREASURY || balanceMinor >= amountMinor;
    }

    public void debit(long amountMinor) {
        this.balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        this.balanceMinor += amountMinor;
    }
}
