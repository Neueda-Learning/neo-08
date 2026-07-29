package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.neobank.module.dto.CardExecuteRequest;
import com.neobank.module.exception.BureauUnavailableException;
import com.neobank.module.exception.InvalidDeliveryAddressException;
import com.neobank.module.model.CardOutcome;
import com.neobank.module.model.CardRecord;
import com.neobank.module.model.IssuingConfig;
import com.neobank.module.repository.CardRecordRepository;
import com.neobank.module.repository.CardTimelineRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardIssuingService {

    private final CardRecordRepository
            cardRecordRepository;

    private final IssuingConfigService
            issuingConfigService;

    private final MockBureauService
            mockBureauService;

    private final CardTimelineRepository
            cardTimelineRepository;

    public CardIssuingService(
            CardRecordRepository cardRecordRepository,
            IssuingConfigService issuingConfigService,
            MockBureauService mockBureauService,
            CardTimelineRepository
                    cardTimelineRepository) {

        this.cardRecordRepository =
                cardRecordRepository;

        this.issuingConfigService =
                issuingConfigService;

        this.mockBureauService =
                mockBureauService;

        this.cardTimelineRepository =
                cardTimelineRepository;
    }

    /**
     * Processes the card application after UC00
     * has inserted the durable IN_PROGRESS row.
     */
    @Transactional
    public void process(
            CardExecuteRequest request) {

        CardRecord record =
                cardRecordRepository
                        .findById(
                                request.applicationId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Card record not found: "
                                                + request
                                                .applicationId()
                                )
                        );

        /*
         * Prevent duplicate processing.
         */
        if (record.getOutcome()
                != CardOutcome.IN_PROGRESS) {

            return;
        }

        IssuingConfig config =
                issuingConfigService
                        .getCurrentConfig();

        try {
            validateDeliveryAddress(
                    request,
                    config
            );

            MockBureauService.BureauResult bureau =
                    mockBureauService.createCard(
                            request.applicationId()
                    );

            /*
             * The case becomes ISSUED only after:
             * 1. delivery validation succeeds;
             * 2. the Bureau accepts the card.
             */
            record.markIssued(
                    bureau.bureauCardId(),
                    bureau.status(),
                    config.getVersion()
            );

            /*
             * UC06 initial timeline entry.
             *
             * Later status changes are written by
             * BureauStatusPoller with source POLL.
             */
            cardTimelineRepository
                    .recordInitialRequested(
                            request.applicationId()
                    );

        } catch (
                InvalidDeliveryAddressException
                        exception) {

            record.markFailed(
                    "CRD_DELIVERY_ADDRESS_INVALID"
            );

        } catch (
                BureauUnavailableException
                        exception) {

            record.markFailed(
                    "CRD_BUREAU_UNAVAILABLE"
            );
        }
    }

    /**
     * Validates the effective delivery address
     * using the current UC08 configuration.
     */
    private void validateDeliveryAddress(
            CardExecuteRequest request,
            IssuingConfig config) {

        JsonNode application =
                request.application();

        if (application == null
                || application.isNull()
                || !application.isObject()) {

            throw new InvalidDeliveryAddressException(
                    "Application is required"
            );
        }

        JsonNode delivery =
                application.get("delivery");

        if (delivery == null
                || delivery.isNull()
                || !delivery.isObject()) {

            throw new InvalidDeliveryAddressException(
                    "Delivery information is required"
            );
        }

        JsonNode useCurrentAddressNode =
                delivery.get(
                        "useCurrentAddress"
                );

        if (useCurrentAddressNode == null
                || useCurrentAddressNode.isNull()
                || !useCurrentAddressNode
                .isBoolean()) {

            throw new InvalidDeliveryAddressException(
                    "delivery.useCurrentAddress "
                            + "is required"
            );
        }

        boolean useCurrentAddress =
                useCurrentAddressNode
                        .booleanValue();

        JsonNode address =
                useCurrentAddress
                        ? getCurrentAddress(
                        application
                )
                        : getAlternativeAddress(
                        delivery
                );

        validateAddressFields(
                address,
                config.getRequiredAddressFields()
        );

        validateDeliveryCountry(
                address,
                config.getDeliveryCountries()
        );
    }

    /**
     * Returns applicant.currentAddress when
     * delivery.useCurrentAddress is true.
     */
    private JsonNode getCurrentAddress(
            JsonNode application) {

        JsonNode applicant =
                application.get("applicant");

        if (applicant == null
                || applicant.isNull()
                || !applicant.isObject()) {

            throw new InvalidDeliveryAddressException(
                    "Applicant information is required"
            );
        }

        JsonNode currentAddress =
                applicant.get(
                        "currentAddress"
                );

        validateAddressObject(
                currentAddress,
                "Applicant current address "
                        + "is required"
        );

        return currentAddress;
    }

    /**
     * Returns delivery.address when
     * delivery.useCurrentAddress is false.
     */
    private JsonNode getAlternativeAddress(
            JsonNode delivery) {

        JsonNode alternativeAddress =
                delivery.get("address");

        validateAddressObject(
                alternativeAddress,
                "Alternative delivery address "
                        + "is required"
        );

        return alternativeAddress;
    }

    private void validateAddressObject(
            JsonNode address,
            String errorMessage) {

        if (address == null
                || address.isNull()
                || !address.isObject()) {

            throw new InvalidDeliveryAddressException(
                    errorMessage
            );
        }
    }

    /**
     * Checks every field configured by UC08.
     */
    private void validateAddressFields(
            JsonNode address,
            List<String> requiredFields) {

        if (requiredFields == null
                || requiredFields.isEmpty()) {

            throw new InvalidDeliveryAddressException(
                    "No required address fields "
                            + "are configured"
            );
        }

        for (String fieldName :
                requiredFields) {

            validateRequiredField(
                    address,
                    fieldName
            );
        }
    }

    private void validateRequiredField(
            JsonNode address,
            String fieldName) {

        JsonNode fieldValue =
                address.get(fieldName);

        if (fieldValue == null
                || fieldValue.isNull()
                || !fieldValue.isTextual()
                || fieldValue.asText()
                .isBlank()) {

            throw new InvalidDeliveryAddressException(
                    "Address field is required: "
                            + fieldName
            );
        }
    }

    /**
     * Checks that the effective delivery country
     * is allowed by the current UC08 config.
     */
    private void validateDeliveryCountry(
            JsonNode address,
            List<String> deliveryCountries) {

        JsonNode countryNode =
                address.get("country");

        if (countryNode == null
                || countryNode.isNull()
                || !countryNode.isTextual()
                || countryNode.asText()
                .isBlank()) {

            throw new InvalidDeliveryAddressException(
                    "Address field is required: "
                            + "country"
            );
        }

        String country =
                countryNode.asText()
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        boolean supported =
                deliveryCountries != null
                        && deliveryCountries
                        .stream()
                        .filter(value ->
                                value != null
                        )
                        .map(value ->
                                value.trim()
                                        .toUpperCase(
                                                Locale.ROOT
                                        )
                        )
                        .anyMatch(
                                country::equals
                        );

        if (!supported) {
            throw new InvalidDeliveryAddressException(
                    "Delivery country is "
                            + "not supported: "
                            + country
            );
        }
    }
}