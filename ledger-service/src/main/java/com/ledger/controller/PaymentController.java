package com.ledger.controller;

import com.ledger.dto.payment.request.PaymentRequest;
import com.ledger.dto.payment.response.PaymentResponse;
import com.ledger.repository.JournalEntryRepository;
import com.ledger.service.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final JournalEntryRepository entries;

    public PaymentController(PaymentService payments, JournalEntryRepository entries) {
        this.payments = payments;
        this.entries = entries;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        var result = payments.createPayment(idempotencyKey,
                request.fromAccountId(), request.toAccountId(),
                request.amountMinor(), request.description());
        // 200 for a replay (nothing new was created), 201 for a fresh posting.
        var status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PaymentResponse.from(result.entry(), result.replayed()));
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return entries.findWithPostingsById(id)
                .map(entry -> PaymentResponse.from(entry, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));
    }
}
