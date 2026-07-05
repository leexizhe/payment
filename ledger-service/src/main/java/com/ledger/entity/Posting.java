package com.ledger.entity;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * One leg of a journal entry: a signed amount against one account.
 * Negative = debit (money out), positive = credit (money in).
 */
@Getter
@Entity
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private long amountMinor;

    protected Posting() {
    }

    public Posting(JournalEntry entry, Account account, long amountMinor) {
        this.entry = entry;
        this.account = account;
        this.amountMinor = amountMinor;
    }
}
