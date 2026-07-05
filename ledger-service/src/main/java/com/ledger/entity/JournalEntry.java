package com.ledger.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One logical payment. The unique constraint on idempotencyKey is the
 * last line of defense against double-posting: even if two retries race
 * past the read-check, only one insert can win.
 */
@Getter
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {
    }

    public JournalEntry(String idempotencyKey, String description) {
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public void addPosting(Account account, long signedAmountMinor) {
        postings.add(new Posting(this, account, signedAmountMinor));
    }

    /** Double-entry invariant: the legs of an entry always sum to zero. */
    public long postingSum() {
        return postings.stream().mapToLong(Posting::getAmountMinor).sum();
    }
}
