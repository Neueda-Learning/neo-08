package com.neobank.module.integrations.failedissues;

/** The fields the module needs from a successful bureau instruction. */
public record BureauAcceptedCard(String bureauCardId, String status) {
}
