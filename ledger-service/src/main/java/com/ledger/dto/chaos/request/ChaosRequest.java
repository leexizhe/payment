package com.ledger.dto.chaos.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {"latencyMs": 800, "errorRate": 0.3} starts an incident; zeros end it. */
public record ChaosRequest(
        @NotNull @Min(0) Long latencyMs,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double errorRate) {
}
