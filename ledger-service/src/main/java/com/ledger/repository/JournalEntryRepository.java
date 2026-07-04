package com.ledger.repository;

import com.ledger.entity.JournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /** Fetch-joined so entries can be serialized outside the transaction. */
    @EntityGraph(attributePaths = {"postings", "postings.account"})
    Optional<JournalEntry> findWithPostingsByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"postings", "postings.account"})
    Optional<JournalEntry> findWithPostingsById(Long id);
}
