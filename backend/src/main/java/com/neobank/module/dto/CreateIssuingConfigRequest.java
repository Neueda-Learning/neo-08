package com.neobank.module.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateIssuingConfigRequest(

        @NotBlank(message = "panPrefix is required")
        @Pattern(
                regexp = "9999\\d{2}",
                message = "panPrefix must be six digits inside the reserved 9999xx test range"
        )
        String panPrefix,

        @NotNull(message = "panLength is required")
        @Min(value = 16, message = "panLength must be 16")
        @Max(value = 16, message = "panLength must be 16")
        Integer panLength,

        @NotEmpty(message = "deliveryCountries must not be empty")
        List<@NotBlank String> deliveryCountries,

        @NotEmpty(message = "requiredAddressFields must not be empty")
        List<@NotBlank String> requiredAddressFields,

        @NotBlank(message = "bureauBaseUrl is required")
        String bureauBaseUrl
) {
}