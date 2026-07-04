package com.ledger.entity;

import com.ledger.enums.AccountType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic unit tests (Surefire): domain rules that need no Spring, no DB,
 * no mocks. Flow logic belongs in the controller ITs, not here.
 */
class LedgerDomainTest {

    @Test
    void walletCannotDebitBelowZero() {
        var wallet = new Account("w", AccountType.WALLET, "USD", 100);

        assertThat(wallet.canDebit(100)).isTrue();
        assertThat(wallet.canDebit(101)).isFalse();
    }

    @Test
    void treasuryMayOverdraw() {
        var treasury = new Account("t", AccountType.TREASURY, "USD", 0);

        assertThat(treasury.canDebit(1_000_000)).isTrue();
    }

    @Test
    void balancedEntrySumsToZero() {
        var from = new Account("a", AccountType.WALLET, "USD", 500);
        var to = new Account("b", AccountType.MERCHANT, "USD", 0);
        var entry = new JournalEntry("key", "test");

        entry.addPosting(from, -300);
        entry.addPosting(to, 300);

        assertThat(entry.postingSum()).isZero();
    }
}
