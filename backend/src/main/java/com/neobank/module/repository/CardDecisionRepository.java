package com.neobank.module.repository;

import com.neobank.module.model.CardDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation from the method name, same as {@code DemoShowcaseRepository}.
 */
public interface CardDecisionRepository extends JpaRepository<CardDecision, Long> {

    /**
     * Newest first, id as tiebreak — MySQL {@code TIMESTAMP} only stores whole seconds, so several
     * decisions made in the same second need a monotonic tiebreak to keep a stable order on the
     * board. See {@code DemoShowcaseRepository} for the fuller explanation.
     */
    List<CardDecision> findAllByOrderByCreatedAtDescIdDesc();
}

