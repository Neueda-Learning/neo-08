package com.neobank.module.repository;

import com.neobank.module.model.CardRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence operations for the new v5 card intake flow. */
public interface CardRecordRepository extends JpaRepository<CardRecord, String> {}
