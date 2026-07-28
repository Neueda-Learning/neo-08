package com.neobank.module.repository;

import com.neobank.module.model.OverrideLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {

    List<OverrideLog> findByApplicationIdOrderByOverriddenAtDesc(String applicationId);
}
