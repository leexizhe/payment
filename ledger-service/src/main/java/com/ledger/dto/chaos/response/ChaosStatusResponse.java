package com.ledger.dto.chaos.response;

public record ChaosStatusResponse(boolean active, long latencyMs, double errorRate) {
}
