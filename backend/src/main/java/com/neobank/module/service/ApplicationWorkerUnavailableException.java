package com.neobank.module.service;

/**
 * Signals that the request was not handed to a worker, so the HTTP layer must
 * not acknowledge it as accepted. The durable intake row remains retryable.
 */
public class ApplicationWorkerUnavailableException extends RuntimeException {

    public ApplicationWorkerUnavailableException(Throwable cause) {
        super("Card issuing is temporarily unavailable; retry the application.", cause);
    }
}
