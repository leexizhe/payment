package com.ledger.controller;

import com.ledger.BaseControllerIT;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static com.ledger.testutil.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

class AccountControllerIT extends BaseControllerIT {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Override
    protected String controllerBasePath() {
        return "/api/accounts";
    }

    @Test
    void listContainsTheLiquibaseSeededWorld() {
        var response = getRequest("", JSON_LIST);

        assertSuccess(response);
        assertThat(response.getBody())
                .extracting(a -> a.get("name"))
                .contains(SEEDED_TREASURY, SEEDED_MERCHANT, SEEDED_WALLET);
    }

    @Test
    void seededWalletBalanceIsBackedByAFundingPosting() {
        var wallet = getRequest("", JSON_LIST).getBody().stream()
                .filter(a -> SEEDED_WALLET.equals(a.get("name")))
                .findFirst().orElseThrow();

        var response = getRequest("/" + wallet.get("id"), JSON_MAP);

        assertSuccess(response);
        assertThat(((Number) response.getBody().get("balanceMinor")).longValue())
                .isEqualTo(SEEDED_WALLET_BALANCE_MINOR);
        var recentPostings = (List<Map<String, Object>>) response.getBody().get("recentPostings");
        assertThat(recentPostings).isNotEmpty();
        assertThat(((Number) recentPostings.get(0).get("amountMinor")).longValue())
                .isEqualTo(SEEDED_WALLET_BALANCE_MINOR);
    }

    @Test
    void unknownAccountIsNotFoundProblem() {
        var response = getRequest("/999999", JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND);
    }
}
