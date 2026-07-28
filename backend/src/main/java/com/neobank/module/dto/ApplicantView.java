package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

public record ApplicantView(
        String applicationId,
        String fullName,
        String productCode,
        Boolean useCurrentAddress,
        AddressView deliveryAddress
) {

    public record AddressView(
            String line1,
            String line2,
            String city,
            String postcode,
            String country
    ) {

        public static AddressView from(
                Application.Address address) {

            if (address == null) {
                return null;
            }

            return new AddressView(
                    address.line1(),
                    address.line2(),
                    address.city(),
                    address.postcode(),
                    address.country()
            );
        }
    }
}