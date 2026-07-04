package com.ledger.testutil;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared domain assertions for tests. */
public final class TestAssertions {

    private TestAssertions() {
    }

    /** Scale-insensitive BigDecimal equality: 10 == 10.00. */
    public static void assertAmountEquals(BigDecimal expected, BigDecimal actual) {
        assertThat(actual)
                .as("expected %s to equal %s ignoring scale", actual, expected)
                .isEqualByComparingTo(expected);
    }

    /** The core double-entry invariant: postings across the ledger sum to zero. */
    public static void assertLedgerBalanced(long postingSum) {
        assertThat(postingSum)
                .as("sum of all postings (money must never be created or destroyed)")
                .isZero();
    }
}
