package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.Application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicantService {

    private final RestClient http;
    private final String applicationsUrl;

    public ApplicantService(
            RestClient http,
            @Value(
                    "${service.orchestrator-url:http://localhost:9000}"
            )
            String orchestratorUrl) {

        this.http = http;
        this.applicationsUrl =
                orchestratorUrl + "/api/v1/applications";
    }

    public ApplicantView getApplicant(
            String applicationId) {

        Application application = fetchApplication(
                applicationId
        );

        if (application.applicant() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orchestrator response has no applicant"
            );
        }

        if (application.product() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orchestrator response has no product"
            );
        }

        if (application.delivery() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orchestrator response has no delivery information"
            );
        }

        Application.Address effectiveAddress =
                selectDeliveryAddress(application);

        return new ApplicantView(
                applicationId,
                application.applicant().fullName(),
                application.product().productCode(),
                application.delivery()
                        .useCurrentAddress(),
                ApplicantView.AddressView.from(
                        effectiveAddress
                )
        );
    }

    private Application fetchApplication(
            String applicationId) {

        try {
            Application application = http.get()
                    .uri(
                            applicationsUrl
                                    + "/{applicationId}",
                            applicationId
                    )
                    .retrieve()
                    .body(Application.class);

            if (application == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Orchestrator returned an empty response"
                );
            }

            return application;

        } catch (HttpClientErrorException.NotFound exception) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Application not found: "
                            + applicationId,
                    exception
            );

        } catch (RestClientException exception) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Orchestrator is currently unavailable",
                    exception
            );
        }
    }

    private Application.Address selectDeliveryAddress(
            Application application) {

        if (Boolean.TRUE.equals(
                application.delivery()
                        .useCurrentAddress())) {

            return application.applicant()
                    .currentAddress();
        }

        return application.delivery().address();
    }
}