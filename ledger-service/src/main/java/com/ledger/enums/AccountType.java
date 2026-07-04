package com.ledger.enums;

public enum AccountType {
    /** Funding source; the only account allowed to go negative (money enters the system here). */
    TREASURY,
    MERCHANT,
    WALLET
}
