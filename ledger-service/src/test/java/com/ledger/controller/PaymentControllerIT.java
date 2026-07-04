package com.ledger.controller;

import com.ledger.BaseControllerIT;
import com.ledger.entity.Account;
import com.ledger.enums.AccountType;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.PostingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ledger.testutil.TestAssertions.assertLedgerBalanced;
import static com.ledger.testutil.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerIT extends BaseControllerIT {

    @Autowired
    AccountRepository accounts;

    @Autowired
    PostingRepository postings;

    @Autowired
    MeterRegistry meterRegistry;

    Account payer;
    Account payee;

    @Override
    protected String controllerBasePath() {
        return "/api/payments";
    }

    @BeforeEach
    void createParties() {
        payer = accounts.save(new Account("payer-" + UUID.randomUUID(),
                AccountType.WALLET, DEFAULT_CURRENCY, DEFAULT_STARTING_BALANCE_MINOR));
        payee = accounts.save(new Account("payee-" + UUID.randomUUID(),
                AccountType.MERCHANT, DEFAULT_CURRENCY, 0));
    }

    private String paymentJson(long amountMinor) {
        return """
                {"fromAccountId": %d, "toAccountId": %d, "amountMinor": %d, "description": "%s"}
                """.formatted(payer.getId(), payee.getId(), amountMinor, DEFAULT_DESCRIPTION);
    }

    private ResponseEntity<Map<String, Object>> postPayment(String idempotencyKey, long amountMinor) {
        return postRequest("", paymentJson(amountMinor), Map.of(IDEMPOTENCY_HEADER, idempotencyKey), JSON_MAP);
    }

    private long balanceOf(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalanceMinor();
    }

    @Test
    void paymentMovesMoneyAndLedgerStaysBalanced() {
        var response = postPayment(UUID.randomUUID().toString(), DEFAULT_AMOUNT_MINOR);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("replayed", false)
                .containsKey("entryId");
        assertThat((List<?>) response.getBody().get("postings")).hasSize(2);

        assertThat(balanceOf(payer)).isEqualTo(DEFAULT_STARTING_BALANCE_MINOR - DEFAULT_AMOUNT_MINOR);
        assertThat(balanceOf(payee)).isEqualTo(DEFAULT_AMOUNT_MINOR);
        assertLedgerBalanced(postings.sumAllPostings());
    }

    @Test
    void duplicateIdempotencyKeyReplaysWithoutDoublePosting() {
        String key = UUID.randomUUID().toString();
        double hitsBefore = meterRegistry.get("ledger.idempotency.hits").counter().count();

        var first = postPayment(key, 1_000);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var replay = postPayment(key, 1_000);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody())
                .containsEntry("replayed", true)
                .containsEntry("entryId", first.getBody().get("entryId"));

        // Money moved exactly once, and the dedup left its metric trail.
        assertThat(balanceOf(payer)).isEqualTo(DEFAULT_STARTING_BALANCE_MINOR - 1_000);
        assertThat(meterRegistry.get("ledger.idempotency.hits").counter().count())
                .isEqualTo(hitsBefore + 1);
    }

    @Test
    void insufficientFundsIsRejectedAsProblem() {
        var response = postPayment(UUID.randomUUID().toString(), 999_999_999);

        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceOf(payer)).isEqualTo(DEFAULT_STARTING_BALANCE_MINOR);
    }

    @Test
    void missingIdempotencyKeyIsRejectedAsProblem() {
        var response = postRequest("", paymentJson(1_000), JSON_MAP);

        assertProblem(response, HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownPaymentIsNotFoundProblem() {
        var response = getRequest("/999999", JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND);
    }
}
