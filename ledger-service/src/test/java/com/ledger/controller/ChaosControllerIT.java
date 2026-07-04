package com.ledger.controller;

import com.ledger.BaseControllerIT;
import com.ledger.entity.Account;
import com.ledger.enums.AccountType;
import com.ledger.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static com.ledger.testutil.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

class ChaosControllerIT extends BaseControllerIT {

    @Autowired
    AccountRepository accounts;

    @Override
    protected String controllerBasePath() {
        return "/api/chaos";
    }

    @AfterEach
    void healTheService() {
        postRequest("", "{\"latencyMs\": 0, \"errorRate\": 0}", JSON_MAP);
    }

    @Test
    void chaosIsInactiveByDefault() {
        var response = getRequest("", JSON_MAP);

        assertSuccess(response);
        assertThat(response.getBody()).containsEntry("active", false);
    }

    @Test
    void fullErrorRateMakesPaymentsUnavailable() {
        var configured = postRequest("", "{\"latencyMs\": 0, \"errorRate\": 1.0}", JSON_MAP);
        assertSuccess(configured);
        assertThat(configured.getBody()).containsEntry("active", true);

        Account from = accounts.save(new Account("chaos-payer-" + UUID.randomUUID(),
                AccountType.WALLET, DEFAULT_CURRENCY, DEFAULT_STARTING_BALANCE_MINOR));
        Account to = accounts.save(new Account("chaos-payee-" + UUID.randomUUID(),
                AccountType.MERCHANT, DEFAULT_CURRENCY, 0));
        String body = """
                {"fromAccountId": %d, "toAccountId": %d, "amountMinor": 100, "description": "%s"}
                """.formatted(from.getId(), to.getId(), DEFAULT_DESCRIPTION);

        var payment = postRequest(serverUrl("/api/payments"), body,
                Map.of(IDEMPOTENCY_HEADER, UUID.randomUUID().toString()), JSON_MAP);

        assertProblem(payment, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void outOfRangeErrorRateIsRejectedAsProblem() {
        var response = postRequest("", "{\"latencyMs\": 0, \"errorRate\": 2.0}", JSON_MAP);

        assertProblem(response, HttpStatus.BAD_REQUEST);
    }
}
