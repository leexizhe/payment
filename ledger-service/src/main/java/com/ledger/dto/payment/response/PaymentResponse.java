package com.ledger.dto.payment.response;

import com.ledger.entity.JournalEntry;

import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        Long entryId,
        String idempotencyKey,
        String description,
        Instant createdAt,
        boolean replayed,
        List<PostingView> postings) {

    public record PostingView(Long accountId, String accountName, long amountMinor) {
    }

    public static PaymentResponse from(JournalEntry entry, boolean replayed) {
        var postings = entry.getPostings().stream()
                .map(p -> new PostingView(p.getAccount().getId(), p.getAccount().getName(), p.getAmountMinor()))
                .toList();
        return new PaymentResponse(entry.getId(), entry.getIdempotencyKey(),
                entry.getDescription(), entry.getCreatedAt(), replayed, postings);
    }
}
