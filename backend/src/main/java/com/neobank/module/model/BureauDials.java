package com.neobank.module.model;

import org.springframework.stereotype.Component;

@Component
public class BureauDials {

    private int secondsPerStage = 120;
    private int latencyMs = 0;
    private boolean killSwitch = false;

    public synchronized void update(
            Integer secondsPerStage,
            Integer latencyMs,
            Boolean killSwitch) {

        if (secondsPerStage != null) {
            this.secondsPerStage = secondsPerStage;
        }

        if (latencyMs != null) {
            this.latencyMs = latencyMs;
        }

        if (killSwitch != null) {
            this.killSwitch = killSwitch;
        }
    }

    public int getSecondsPerStage() {
        return secondsPerStage;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }
}