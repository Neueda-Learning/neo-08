package com.neobank.module.dto;

/** The exact three-field v5 acknowledgement body. */
public record CardExecuteAck(
        String status,
        String applicationId,
        String command) {

    public static CardExecuteAck inProgress(CardExecuteRequest request) {
        return new CardExecuteAck("in-progress", request.applicationId(), request.command());
    }
}
