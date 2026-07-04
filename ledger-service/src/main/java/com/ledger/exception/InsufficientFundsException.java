package com.ledger.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountName, long balanceMinor, long requestedMinor) {
        super("Insufficient funds in '%s': balance %d, requested %d (minor units)"
                .formatted(accountName, balanceMinor, requestedMinor));
    }
}
