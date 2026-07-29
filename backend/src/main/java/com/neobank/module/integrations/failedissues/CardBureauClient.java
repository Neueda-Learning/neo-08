package com.neobank.module.integrations.failedissues;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** UC-04 HTTP client for {@code POST /bureau/cards}. */
@Component
public class CardBureauClient {

    private static final Logger log = LoggerFactory.getLogger(CardBureauClient.class);
    private final RestClient http;

    public CardBureauClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(requests).build();
    }

    public Optional<BureauAcceptedCard> issue(
            String bureauBaseUrl, BureauCardInstruction instruction) {
        try {
            JsonNode response = http.post()
                    .uri(trimTrailingSlash(bureauBaseUrl) + "/bureau/cards")
                    .body(instruction)
                    .retrieve()
                    .body(JsonNode.class);
            String cardId = firstText(response, "bureauCardId", "cardId", "id");
            String status = firstText(response, "status");
            if (cardId == null || !"REQUESTED".equals(status)) {
                log.warn("Bureau returned an incomplete issue acknowledgement");
                return Optional.empty();
            }
            return Optional.of(new BureauAcceptedCard(cardId, status));
        } catch (RestClientException unavailable) {
            log.warn("Card bureau is unavailable: {}", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("IssuingConfig bureauBaseUrl is empty");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String firstText(JsonNode response, String... names) {
        if (response == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = response.get(name);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
