package com.neobank.module.integrations.cardsearch;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-01's read-only orchestrator integration.
 *
 * <p>This is separate from the fixed callback client because it serves a different contract:
 * resolving applicant names and proxying an application for live UI hydration.</p>
 */
@Component
public class CardApplicationClient {

    private static final Logger log = LoggerFactory.getLogger(CardApplicationClient.class);

    private final RestClient http;
    private final String applicationsUrl;

    public CardApplicationClient(
            RestClient http,
            @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    /**
     * Resolves a name to application ids. An unavailable orchestrator degrades to no matches
     * instead of turning the local search endpoint into a 500.
     */
    public List<String> findApplicationIdsByName(String name) {
        try {
            JsonNode response = http.get()
                    .uri(applicationsUrl + "?name={name}", name)
                    .retrieve()
                    .body(JsonNode.class);
            Set<String> ids = new LinkedHashSet<>();
            collectApplicationIds(response, ids);
            return new ArrayList<>(ids);
        } catch (RestClientException failure) {
            log.warn("Applicant name search is unavailable for '{}': {}", name, failure.toString());
            return List.of();
        }
    }

    /** Fetches the live application object without persisting any part of it. */
    public JsonNode getApplication(String applicationId) {
        try {
            return http.get()
                    .uri(applicationsUrl + "/{applicationId}", applicationId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "application not found: " + applicationId, notFound);
        } catch (RestClientException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "applicant data is temporarily unavailable",
                    failure);
        }
    }

    private static void collectApplicationIds(JsonNode node, Set<String> ids) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            if (!node.asText().isBlank()) {
                ids.add(node.asText());
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectApplicationIds(item, ids));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode applicationId = node.get("applicationId");
        if (applicationId != null && applicationId.isTextual()
                && !applicationId.asText().isBlank()) {
            ids.add(applicationId.asText());
        }

        for (String container : List.of("applications", "items", "content", "results", "application")) {
            JsonNode child = node.get(container);
            if (child != null) {
                collectApplicationIds(child, ids);
            }
        }
    }
}
