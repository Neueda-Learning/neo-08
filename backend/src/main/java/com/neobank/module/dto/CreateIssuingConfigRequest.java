package com.neobank.module.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateIssuingConfigRequest(

        @NotBlank(message = "panPrefix is required")
        @Pattern(
                regexp = "9999\\d{2}",
                message = "panPrefix must be six digits inside the reserved 9999xx test range"
        )
        String panPrefix,

        @NotNull(message = "panLength is required")
        @Min(
                value = 16,
                message = "panLength must be 16"
        )
        @Max(
                value = 16,
                message = "panLength must be 16"
        )
        Integer panLength,

        @NotEmpty(
                message = "deliveryCountries must not be empty"
        )
        @Size(
                max = 20,
                message = "deliveryCountries must contain at most 20 countries"
        )
        List<
                @NotBlank(
                        message = "country code must not be blank"
                )
                @Pattern(
                        regexp = "[A-Z]{2}",
                        message = "country code must be an uppercase ISO alpha-2 code"
                )
                        String
                > deliveryCountries,

        @NotEmpty(
                message = "requiredAddressFields must not be empty"
        )
        @Size(
                max = 5,
                message = "requiredAddressFields must contain at most 5 fields"
        )
        List<
                @NotBlank(
                        message = "address field must not be blank"
                )
                @Pattern(
                        regexp = "line1|line2|city|postcode|country",
                        message = "address field must be one of: line1, line2, city, postcode, country"
                )
                        String
                > requiredAddressFields,

        @NotBlank(
                message = "bureauBaseUrl is required"
        )
        @Size(
                max = 256,
                message = "bureauBaseUrl must not exceed 256 characters"
        )
        @Pattern(
                regexp = "https?://.+",
                message = "bureauBaseUrl must be a valid HTTP or HTTPS URL"
        )
        String bureauBaseUrl

) {
}