package com.ledger.dto.payment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @Positive Long amountMinor,
        @NotBlank String description) {
}
