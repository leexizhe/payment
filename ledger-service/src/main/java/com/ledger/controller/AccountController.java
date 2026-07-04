package com.ledger.controller;

import com.ledger.dto.account.response.AccountResponse;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.PostingRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accounts;
    private final PostingRepository postings;

    public AccountController(AccountRepository accounts, PostingRepository postings) {
        this.accounts = accounts;
        this.postings = postings;
    }

    @GetMapping
    public List<AccountResponse> listAccounts() {
        return accounts.findAll().stream()
                .map(a -> AccountResponse.from(a, List.of()))
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        var account = accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        var recent = postings.findTop10ByAccountIdOrderByIdDesc(id);
        return AccountResponse.from(account, recent);
    }
}
