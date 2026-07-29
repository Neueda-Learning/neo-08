package com.neobank.module.integrations.cardissue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP boundary used only by the UC-02 initial card-issue flow. */
@Component
public class CardIssueBureauClient {

    private static final Logger log = LoggerFactory.getLogger(CardIssueBureauClient.class);
    private final RestClient http;

    public CardIssueBureauClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(requests).build();
    }

    public Optional<AcceptedCard> issue(String bureauBaseUrl, CardInstruction instruction) {
        try {
            JsonNode response = http.post()
                    .uri(trimTrailingSlash(bureauBaseUrl) + "/bureau/cards")
                    .body(instruction)
                    .retrieve()
                    .body(JsonNode.class);
            String cardId = firstText(response, "bureauCardId", "cardId", "id");
            String status = firstText(response, "status");
            if (cardId == null || !"REQUESTED".equals(status)) {
                log.warn("Card bureau returned an incomplete issue acknowledgement");
                return Optional.empty();
            }
            return Optional.of(new AcceptedCard(cardId));
        } catch (RestClientException unavailable) {
            // Never log the instruction: it is the only object that temporarily contains the PAN.
            log.warn("Card bureau is unavailable: {}", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("IssuingConfig bureauBaseUrl is empty");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String firstText(JsonNode response, String... fields) {
        if (response == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = response.get(field);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    public record CardInstruction(
            String applicationId,
            String reference,
            String pan,
            String applicantName,
            DeliveryAddress deliveryAddress,
            String accountId,
            String productCode) {
    }

    public record DeliveryAddress(
            String line1,
            String line2,
            String city,
            String postcode,
            String country) {
    }

    public record AcceptedCard(String bureauCardId) {
    }
}
