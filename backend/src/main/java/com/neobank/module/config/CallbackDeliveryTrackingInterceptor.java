package com.neobank.module.config;

import com.neobank.module.service.CallbackDeliveryContext;
import com.neobank.module.service.CardRecordStore;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Completes the local callback outbox after the fixed orchestrator client
 * receives a successful HTTP response.
 *
 * <p>The interceptor matches the configured orchestrator origin, method and
 * exact application-resource path. It never inspects or logs request bodies.
 * If recording success fails, the outbox remains pending and the idempotent
 * PUT is retried later.</p>
 */
@Component
public final class CallbackDeliveryTrackingInterceptor
        implements ClientHttpRequestInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(CallbackDeliveryTrackingInterceptor.class);
    private static final String APPLICATIONS_PATH = "/api/v1/applications/";

    private final CardRecordStore records;
    private final CallbackDeliveryContext context;
    private final URI orchestratorOrigin;
    private final String callbackPathPrefix;

    public CallbackDeliveryTrackingInterceptor(
            CardRecordStore records,
            CallbackDeliveryContext context,
            @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.records = records;
        this.context = context;
        URI configured = URI.create(orchestratorUrl);
        this.orchestratorOrigin = URI.create(
                configured.getScheme()
                        + "://"
                        + configured.getAuthority());
        String basePath = configured.getPath();
        this.callbackPathPrefix =
                (basePath == null || basePath.isBlank() || "/".equals(basePath)
                                ? ""
                                : basePath.replaceFirst("/+$", ""))
                        + APPLICATIONS_PATH;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (isSuccessfulCallback(request, response)) {
            String applicationId = applicationId(request.getURI());
            context.currentToken().ifPresent(token ->
                    recordDelivery(applicationId, token));
        }
        return response;
    }

    private boolean isSuccessfulCallback(
            HttpRequest request,
            ClientHttpResponse response) throws IOException {
        return request.getMethod() == HttpMethod.PUT
                && sameOrigin(request.getURI())
                && applicationId(request.getURI()) != null
                && response.getStatusCode().is2xxSuccessful();
    }

    private boolean sameOrigin(URI target) {
        return normalized(orchestratorOrigin.getScheme()).equals(normalized(target.getScheme()))
                && normalized(orchestratorOrigin.getHost()).equals(normalized(target.getHost()))
                && effectivePort(orchestratorOrigin) == effectivePort(target);
    }

    private String applicationId(URI target) {
        String path = target.getPath();
        if (path == null || !path.startsWith(callbackPathPrefix)) {
            return null;
        }
        String encoded = path.substring(callbackPathPrefix.length());
        if (encoded.isBlank() || encoded.contains("/")) {
            return null;
        }
        String decoded = UriUtils.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
        return decoded.isBlank() || decoded.contains("/") ? null : decoded;
    }

    private void recordDelivery(String applicationId, String token) {
        if (applicationId == null) {
            return;
        }
        try {
            if (records.markCallbackDelivered(applicationId, token, Instant.now())) {
                log.info("DELIVERED callback for {}", applicationId);
            }
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Callback delivery receipt could not be stored for {}; retry remains pending (type={})",
                    applicationId,
                    persistenceFailure.getClass().getSimpleName());
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
