package com.ledger.service.payment;

import com.ledger.entity.JournalEntry;

/** replayed = the idempotency key already existed and we returned the original entry. */
public record PaymentResult(JournalEntry entry, boolean replayed) {
}
