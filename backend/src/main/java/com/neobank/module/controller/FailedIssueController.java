//package com.neobank.module.controller;
//
//import com.neobank.module.dto.CardRetryAck;
//import com.neobank.module.dto.CardRetryRequest;
//import com.neobank.module.dto.FailedIssueView;
//import com.neobank.module.service.FailedIssueException;
//import com.neobank.module.service.FailedIssueService;
//import java.time.Instant;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RestController;
//
///** UC-04 failed-issues queue and retry endpoints. */
//@RestController
//public class FailedIssueController {
//
//    private final FailedIssueService failedIssues;
//
//    public FailedIssueController(FailedIssueService failedIssues) {
//        this.failedIssues = failedIssues;
//    }
//
//    @GetMapping("/queue")
//    public List<FailedIssueView> queue() {
//        return failedIssues.queue();
//    }
//
////    @PostMapping("/cases/{applicationId}/retry")
////    public ResponseEntity<CardRetryAck> retry(
////            @PathVariable String applicationId,
////            @RequestBody(required = false) CardRetryRequest request) {
////        failedIssues.retry(applicationId, request);
////        return ResponseEntity.accepted().body(CardRetryAck.retrying());
////    }
//@PostMapping("/cases/{applicationId}/retry")
//public ResponseEntity<CardRetryAck> retry(
//        @PathVariable String applicationId,
//        @RequestBody(required = false) CardRetryRequest request) {
//
//    failedIssues.retry(applicationId, request);
//
//    return ResponseEntity
//            .accepted()
//            .body(CardRetryAck.retrying());
//}
//    @ExceptionHandler(FailedIssueException.class)
//    public ResponseEntity<Map<String, Object>> failedIssue(FailedIssueException exception) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("timestamp", Instant.now().toString());
//        body.put("status", exception.status().value());
//        body.put("error", exception.status().getReasonPhrase());
//        body.put("message", exception.getMessage());
//        if (!exception.fieldErrors().isEmpty()) {
//            body.put("fieldErrors", exception.fieldErrors());
//        }
//        return ResponseEntity.status(exception.status()).body(body);
//    }
//}
package com.neobank.module.controller;

import com.neobank.module.dto.CardRetryAck;
import com.neobank.module.dto.CardRetryRequest;
import com.neobank.module.dto.FailedIssueView;
import com.neobank.module.service.FailedIssueException;
import com.neobank.module.service.FailedIssueService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** UC-04 failed-issues queue and retry endpoints. */
@RestController
public class FailedIssueController {

    private final FailedIssueService failedIssues;

    public FailedIssueController(
            FailedIssueService failedIssues) {

        this.failedIssues = failedIssues;
    }

    @GetMapping("/queue")
    public List<FailedIssueView> queue() {
        return failedIssues.queue();
    }

    @PostMapping("/cases/{applicationId}/retry")
    public ResponseEntity<CardRetryAck> retry(
            @PathVariable String applicationId,
            @RequestBody(required = false)
            CardRetryRequest request) {

        failedIssues.retry(
                applicationId,
                request);

        return ResponseEntity
                .accepted()
                .body(CardRetryAck.retrying());
    }

    @ExceptionHandler(FailedIssueException.class)
    public ResponseEntity<Map<String, Object>> failedIssue(
            FailedIssueException exception) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "timestamp",
                Instant.now().toString());

        body.put(
                "status",
                exception.status().value());

        body.put(
                "error",
                exception.status().getReasonPhrase());

        body.put(
                "message",
                exception.getMessage());

        if (!exception.fieldErrors().isEmpty()) {
            body.put(
                    "fieldErrors",
                    exception.fieldErrors());
        }

        return ResponseEntity
                .status(exception.status())
                .body(body);
    }
}