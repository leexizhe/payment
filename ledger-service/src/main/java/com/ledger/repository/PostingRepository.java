package com.ledger.repository;

import com.ledger.entity.Posting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    /** Fetch-joined so the entry can be serialized outside the transaction. */
    @EntityGraph(attributePaths = "entry")
    List<Posting> findTop10ByAccountIdOrderByIdDesc(Long accountId);

    /** Global invariant check: every posting across the ledger must sum to zero. */
    @Query("select coalesce(sum(p.amountMinor), 0) from Posting p")
    long sumAllPostings();
}
