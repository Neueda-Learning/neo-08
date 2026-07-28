package com.neobank.module.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CreateIssuingConfigRequest;
import com.neobank.module.dto.CreateIssuingConfigResponse;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.repository.IssuingConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class IssuingConfigService {

    private static final Set<String> ALLOWED_ADDRESS_FIELDS =
            Set.of(
                    "line1",
                    "line2",
                    "city",
                    "postcode",
                    "country"
            );

    private final IssuingConfigRepository repository;
    private final ObjectMapper objectMapper;

    public IssuingConfigService(
            IssuingConfigRepository repository,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateIssuingConfigResponse createVersion(
            CreateIssuingConfigRequest request) {

        validateCreateRequest(request);

        int nextVersion = repository
                .findTopByOrderByVersionDesc()
                .map(config -> config.getVersion() + 1)
                .orElse(1);

        String deliveryCountriesJson =
                toJson(request.deliveryCountries());

        String requiredAddressFieldsJson =
                toJson(request.requiredAddressFields());

        IssuingConfig config = new IssuingConfig(
                nextVersion,
                request.panPrefix(),
                request.panLength(),
                request.deliveryCountries(),
                request.requiredAddressFields(),
                request.bureauBaseUrl(),
                Instant.now()
        );

        IssuingConfig savedConfig =
                repository.save(config);

        return new CreateIssuingConfigResponse(
                savedConfig.getVersion()
        );
    }

    private void validateCreateRequest(
            CreateIssuingConfigRequest request) {

        if (request.panLength() != 16) {
            throw new IllegalArgumentException(
                    "panLength must be 16"
            );
        }

        if (!ALLOWED_ADDRESS_FIELDS.containsAll(
                request.requiredAddressFields())) {

            throw new IllegalArgumentException(
                    "requiredAddressFields contains an unknown field"
            );
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to convert issuing config list to JSON",
                    exception
            );
        }
    }
}