package com.ledger.service.payment;

import com.ledger.entity.Account;
import com.ledger.entity.JournalEntry;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.observability.LedgerMetrics;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.JournalEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PaymentService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final LedgerMetrics metrics;
    private final TransactionTemplate tx;

    public PaymentService(AccountRepository accounts,
                          JournalEntryRepository entries,
                          LedgerMetrics metrics,
                          PlatformTransactionManager txManager) {
        this.accounts = accounts;
        this.entries = entries;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Posts a balanced double-entry payment, deduplicated by idempotency key.
     * Retrying with the same key returns the original entry and never moves
     * money twice.
     */
    public PaymentResult createPayment(String idempotencyKey,
                                       Long fromAccountId,
                                       Long toAccountId,
                                       long amountMinor,
                                       String description) {
        var existing = entries.findWithPostingsByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            metrics.recordIdempotencyHit();
            return new PaymentResult(existing.get(), true);
        }

        try {
            JournalEntry entry = metrics.timePosting(
                    () -> tx.execute(status ->
                            post(idempotencyKey, fromAccountId, toAccountId, amountMinor, description)));
            metrics.recordPosting(amountMinor);
            return new PaymentResult(entry, false);
        } catch (DataIntegrityViolationException race) {
            // Two retries raced past the read-check; the unique constraint on
            // idempotency_key let exactly one win. Return the winner's entry.
            log.info("Idempotency race on key {}: returning existing entry", idempotencyKey);
            metrics.recordIdempotencyHit();
            return entries.findWithPostingsByIdempotencyKey(idempotencyKey)
                    .map(entry -> new PaymentResult(entry, true))
                    .orElseThrow(() -> race);
        }
    }

    private JournalEntry post(String idempotencyKey,
                              Long fromAccountId,
                              Long toAccountId,
                              long amountMinor,
                              String description) {
        Account from = accounts.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account to = accounts.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException(toAccountId));

        if (!from.canDebit(amountMinor)) {
            throw new InsufficientFundsException(from.getName(), from.getBalanceMinor(), amountMinor);
        }

        from.debit(amountMinor);
        to.credit(amountMinor);

        JournalEntry entry = new JournalEntry(idempotencyKey, description);
        entry.addPosting(from, -amountMinor);
        entry.addPosting(to, amountMinor);

        if (entry.postingSum() != 0) {
            // Should be impossible; this is the invariant double-entry exists to protect.
            throw new IllegalStateException("Unbalanced journal entry: sum=" + entry.postingSum());
        }

        return entries.save(entry);
    }
}
