package com.ledger.dto.account.response;

import com.ledger.entity.Account;
import com.ledger.entity.Posting;

import java.util.List;

public record AccountResponse(
        Long id,
        String name,
        String type,
        String currency,
        long balanceMinor,
        List<PostingView> recentPostings) {

    public record PostingView(Long entryId, long amountMinor) {
    }

    public static AccountResponse from(Account account, List<Posting> recent) {
        var postings = recent.stream()
                .map(p -> new PostingView(p.getEntry().getId(), p.getAmountMinor()))
                .toList();
        return new AccountResponse(account.getId(), account.getName(), account.getType().name(),
                account.getCurrency(), account.getBalanceMinor(), postings);
    }
}
