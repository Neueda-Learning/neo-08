package com.neobank.module.service;

import com.neobank.module.dto.CardDetailView;
import com.neobank.module.dto.CardReasonView;
import com.neobank.module.repository.CardDetailRepository;
import com.neobank.module.repository.CardDetailRepository.StoredCardDetail;
import java.util.List;
import org.springframework.stereotype.Service;

/** Builds a masked UC-02 detail response exclusively from locally stored card data. */
@Service
public class CardDetailService {

    private static final String MASK_PREFIX = "**** **** **** ";

    private final CardDetailRepository cards;

    public CardDetailService(CardDetailRepository cards) {
        this.cards = cards;
    }

    public CardDetailView get(String applicationId) {
        StoredCardDetail card = cards.findByApplicationId(applicationId)
                .orElseThrow(() -> new CardDetailNotFoundException(applicationId));
        return new CardDetailView(
                card.outcome(),
                card.reference(),
                card.panLast4() == null ? null : MASK_PREFIX + card.panLast4(),
                card.panHash(),
                card.bureauCardId(),
                card.bureauStatus(),
                card.dispatchRef(),
                card.accountId(),
                card.productCode(),
                card.issuingConfigVersion(),
                reasons(card));
    }

    private static List<CardReasonView> reasons(StoredCardDetail card) {
        if ("ISSUED".equals(card.outcome())) {
            return List.of(new CardReasonView("CRD_ISSUED"));
        }
        if ("FAILED".equals(card.outcome()) && card.failureReason() != null) {
            return List.of(new CardReasonView(card.failureReason()));
        }
        return List.of();
    }
}
