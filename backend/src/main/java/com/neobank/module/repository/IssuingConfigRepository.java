package com.neobank.module.repository;

import com.neobank.module.model.IssuingConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuingConfigRepository extends JpaRepository<IssuingConfig, Integer> {

    /** 当前生效配置 = MAX(version)。 */
    Optional<IssuingConfig> findTopByOrderByVersionDesc();

    
}
