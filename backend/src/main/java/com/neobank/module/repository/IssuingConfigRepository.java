package com.neobank.module.repository;

import com.neobank.module.model.IssuingConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuingConfigRepository extends JpaRepository<IssuingConfig, Integer> {

    /** Current policy is the highest immutable version. */
    Optional<IssuingConfig> findTopByOrderByVersionDesc();
}
