package com.neobank.module.dto;

import java.util.List;

/** Internal search result; the controller puts {@code hasMore} in an HTTP response header. */
public record CardSearchResult(List<CardCaseSummary> cases, boolean hasMore) {

    public CardSearchResult {
        cases = List.copyOf(cases);
    }

    public static CardSearchResult empty() {
        return new CardSearchResult(List.of(), false);
    }
}
