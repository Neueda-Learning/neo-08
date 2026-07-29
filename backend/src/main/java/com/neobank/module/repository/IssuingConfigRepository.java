package com.neobank.module.repository;

import com.neobank.module.model.IssuingConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuingConfigRepository
        extends JpaRepository<IssuingConfig, Integer> {

    /**
     * The configuration with the highest version is the
     * currently active issuing configuration.
     */
    Optional<IssuingConfig> findTopByOrderByVersionDesc();

    /**
     * Returns the complete configuration history,
     * with the newest version first.
     */
    List<IssuingConfig> findAllByOrderByVersionDesc();
}