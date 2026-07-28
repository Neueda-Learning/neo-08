package com.neobank.module.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Carries the durable outbox claim through the fixed callback client to the
 * HTTP response interceptor. The value is thread-scoped and always cleared.
 */
@Component
public final class CallbackDeliveryContext {

    private final ThreadLocal<String> callbackToken = new ThreadLocal<>();

    public void runWithToken(String token, Runnable callback) {
        if (token == null || token.isBlank()) {
            callback.run();
            return;
        }
        if (callbackToken.get() != null) {
            throw new IllegalStateException("nested callback delivery is not supported");
        }
        callbackToken.set(token);
        try {
            callback.run();
        } finally {
            callbackToken.remove();
        }
    }

    public Optional<String> currentToken() {
        return Optional.ofNullable(callbackToken.get());
    }
}
