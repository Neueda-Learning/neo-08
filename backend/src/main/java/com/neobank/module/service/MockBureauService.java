package com.neobank.module.service;

import com.neobank.module.exception.BureauUnavailableException;
import com.neobank.module.model.BureauDials;
import com.neobank.module.model.BureauStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MockBureauService {

    private final BureauDials bureauDials;

    public MockBureauService(BureauDials bureauDials) {
        this.bureauDials = bureauDials;
    }

    public BureauResult createCard(String applicationId) {
        if (bureauDials.isKillSwitch()) {
            throw new BureauUnavailableException(
                    "Mock bureau is unavailable"
            );
        }

        return new BureauResult(
                "bur-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8),
                BureauStatus.REQUESTED
        );
    }

    public record BureauResult(
            String bureauCardId,
            BureauStatus status) {
    }
}