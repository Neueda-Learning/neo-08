package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.service.ApplicantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases")
public class ApplicantController {

    private final ApplicantService applicantService;

    public ApplicantController(
            ApplicantService applicantService) {

        this.applicantService =
                applicantService;
    }

    @GetMapping("/{applicationId}/applicant")
    public ResponseEntity<ApplicantView> getApplicant(
            @PathVariable String applicationId) {

        ApplicantView applicant =
                applicantService.getApplicant(
                        applicationId
                );

        return ResponseEntity.ok(applicant);
    }
}