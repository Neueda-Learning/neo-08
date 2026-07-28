package com.neobank.module.repository;

import com.neobank.module.model.CardStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardStatusHistoryRepository extends JpaRepository<CardStatusHistory, Long> {

    List<CardStatusHistory> findByApplicationIdOrderByObservedAtAsc(String applicationId);
}
