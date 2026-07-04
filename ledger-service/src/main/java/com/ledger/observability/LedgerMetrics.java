package com.ledger.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Custom business metrics for the ledger. Auto-instrumented HTTP metrics
 * (http_server_requests_*) come for free from Actuator; these are the
 * domain-specific signals that actually tell you the ledger is healthy.
 */
@Component
public class LedgerMetrics {

    private final Counter postingsCreated;
    private final Counter idempotencyHits;
    private final DistributionSummary postingAmount;
    private final Timer postingLatency;

    public LedgerMetrics(MeterRegistry registry) {
        this.postingsCreated = Counter.builder("ledger.postings.created")
                .description("Journal entries successfully posted")
                .register(registry);

        // Ties directly to your idempotency-key work: how often a duplicate
        // redemption was short-circuited instead of double-posting.
        this.idempotencyHits = Counter.builder("ledger.idempotency.hits")
                .description("Requests deduplicated by an existing idempotency key")
                .register(registry);

        this.postingAmount = DistributionSummary.builder("ledger.posting.amount")
                .description("Distribution of posting amounts")
                .baseUnit("currency_minor_units")
                .register(registry);

        this.postingLatency = Timer.builder("ledger.posting.latency")
                .description("Time to persist a double-entry posting")
                // Buckets (not client-side quantiles) so Prometheus can compute
                // any percentile server-side and aggregate across instances
                .publishPercentileHistogram()
                .register(registry);
    }

    /** Call after a posting commits successfully. */
    public void recordPosting(long amountMinor) {
        postingsCreated.increment();
        postingAmount.record(amountMinor);
    }

    /** Call when an idempotency key already exists and you skip the write. */
    public void recordIdempotencyHit() {
        idempotencyHits.increment();
    }

    /**
     * Wrap the persistence call to time it, e.g.:
     *   ledgerMetrics.timePosting(() -> repository.save(entry));
     */
    public <T> T timePosting(Supplier<T> work) {
        return postingLatency.record(work);
    }
}
