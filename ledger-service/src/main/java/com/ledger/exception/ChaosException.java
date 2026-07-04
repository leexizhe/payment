package com.ledger.exception;

/** Mapped to HTTP 503 — a synthetic outage for dashboard/alert practice. */
public class ChaosException extends RuntimeException {

    public ChaosException() {
        super("Chaos injection: synthetic failure");
    }
}
