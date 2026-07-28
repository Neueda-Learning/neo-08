package com.neobank.module.repository;

import com.neobank.module.model.CardRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRecordRepository extends JpaRepository<CardRecord, String> {

    List<CardRecord> findAllByOrderByCreatedAtDesc();

    /** 幂等性检查：applicationId 是否已存在 */
    boolean existsByApplicationId(String applicationId);
}
