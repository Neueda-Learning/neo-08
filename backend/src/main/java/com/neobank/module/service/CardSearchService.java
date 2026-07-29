package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardCaseSummary;
import com.neobank.module.dto.CardSearchResult;
import com.neobank.module.integrations.cardsearch.CardApplicationClient;
import com.neobank.module.repository.CardSearchRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** UC-01 application-id/name search and live applicant hydration. */
@Service
public class CardSearchService {

    private static final int MAX_RESULTS = 10;

    private final CardSearchRepository cards;
    private final CardApplicationClient applications;

    public CardSearchService(CardSearchRepository cards, CardApplicationClient applications) {
        this.cards = cards;
        this.applications = applications;
    }

    public CardSearchResult search(String rawQuery, int requestedLimit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return CardSearchResult.empty();
        }

        int limit = normalizeLimit(requestedLimit);

        Optional<CardCaseSummary> exactIdMatch = cards.findByApplicationId(query);
        if (exactIdMatch.isPresent()) {
            return new CardSearchResult(List.of(exactIdMatch.get()), false);
        }

        List<String> applicationIds = applications.findApplicationIdsByName(query);
        List<CardCaseSummary> matches = cards.findByApplicationIds(applicationIds, limit + 1);
        boolean hasMore = matches.size() > limit;
        List<CardCaseSummary> visible = hasMore ? matches.subList(0, limit) : matches;
        return new CardSearchResult(visible, hasMore);
    }

    public CardSearchResult listAll(int requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        List<CardCaseSummary> matches = cards.findLatest(limit + 1);
        boolean hasMore = matches.size() > limit;
        List<CardCaseSummary> visible = hasMore ? matches.subList(0, limit) : matches;
        return new CardSearchResult(visible, hasMore);
    }

    public JsonNode getApplicant(String applicationId) {
        return applications.getApplication(applicationId);
    }

    private int normalizeLimit(int requestedLimit) {
        return Math.max(1, Math.min(requestedLimit, MAX_RESULTS));
    }
}
