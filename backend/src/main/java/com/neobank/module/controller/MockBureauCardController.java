package com.neobank.module.controller;

import com.neobank.module.model.BureauStatus;
import com.neobank.module.service.MockBureauService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bureau/cards")
public class MockBureauCardController {

    private final MockBureauService
            mockBureauService;

    public MockBureauCardController(
            MockBureauService mockBureauService) {

        this.mockBureauService =
                mockBureauService;
    }

    @PostMapping
    public ResponseEntity<BureauCardResponse>
    createCard(
            @Valid
            @RequestBody
            CreateBureauCardRequest request) {

        MockBureauService.BureauResult result =
                mockBureauService.createCard(
                        request.applicationId()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(result));
    }

    @GetMapping("/{bureauCardId}")
    public ResponseEntity<BureauCardResponse>
    getCard(
            @PathVariable
            String bureauCardId) {

        MockBureauService.BureauResult result =
                mockBureauService.getCard(
                        bureauCardId
                );

        return ResponseEntity.ok(
                toResponse(result)
        );
    }

    private BureauCardResponse toResponse(
            MockBureauService.BureauResult result) {

        return new BureauCardResponse(
                result.bureauCardId(),
                result.applicationId(),
                result.status(),
                result.dispatchRef()
        );
    }

    public record CreateBureauCardRequest(

            @NotBlank(
                    message =
                            "applicationId is required"
            )
            @Size(
                    max = 64,
                    message =
                            "applicationId must not "
                                    + "exceed 64 characters"
            )
            String applicationId

    ) {
    }

    public record BureauCardResponse(
            String bureauCardId,
            String applicationId,
            BureauStatus status,
            String dispatchRef
    ) {
    }
}