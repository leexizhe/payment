package com.ledger.testutil;

/** Shared test values — static-import these instead of scattering literals. */
public final class TestConstants {

    public static final String DEFAULT_CURRENCY = "USD";
    public static final long DEFAULT_AMOUNT_MINOR = 2_500;
    public static final long DEFAULT_STARTING_BALANCE_MINOR = 10_000;
    public static final String DEFAULT_DESCRIPTION = "test payment";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    /** Accounts created by the Liquibase seed changesets. */
    public static final String SEEDED_TREASURY = "treasury";
    public static final String SEEDED_MERCHANT = "acme-merchant";
    public static final String SEEDED_WALLET = "wallet-alice";
    public static final long SEEDED_WALLET_BALANCE_MINOR = 50_000;

    private TestConstants() {
    }
}
