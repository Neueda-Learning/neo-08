package com.neobank.module.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.service.CallbackDeliveryContext;
import com.neobank.module.service.CardRecordStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class CallbackDeliveryTrackingInterceptorTest {

    private static final String TOKEN = "00000000-0000-0000-0000-000000000009";

    private CardRecordStore records;
    private CallbackDeliveryContext context;
    private CallbackDeliveryTrackingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        records = mock(CardRecordStore.class);
        context = new CallbackDeliveryContext();
        interceptor = new CallbackDeliveryTrackingInterceptor(
                records,
                context,
                "http://orchestrator:8080");
    }

    @Test
    void successfulExactCallbackCompletesTheClaimedOutboxRow() throws Exception {
        HttpRequest request = request(
                "http://orchestrator:8080/api/v1/applications/APP-123");
        ClientHttpResponse response = response(HttpStatus.NO_CONTENT);
        ClientHttpRequestExecution execution = execution(response);
        when(records.markCallbackDelivered(
                        eq("APP-123"),
                        eq(TOKEN),
                        any(Instant.class)))
                .thenReturn(true);

        context.runWithToken(TOKEN, () -> intercept(request, execution));

        verify(records).markCallbackDelivered(
                eq("APP-123"),
                eq(TOKEN),
                any(Instant.class));
        assertThat(context.currentToken()).isEmpty();
    }

    @Test
    void failedResponseAndDifferentOriginLeaveTheOutboxPending() throws Exception {
        HttpRequest failedCallback = request(
                "http://orchestrator:8080/api/v1/applications/APP-FAIL");
        HttpRequest otherHost = request(
                "http://bureau:8080/api/v1/applications/APP-OTHER");

        context.runWithToken(
                TOKEN,
                () -> intercept(failedCallback, execution(response(HttpStatus.BAD_GATEWAY))));
        context.runWithToken(
                TOKEN,
                () -> intercept(otherHost, execution(response(HttpStatus.NO_CONTENT))));

        verify(records, never()).markCallbackDelivered(
                any(),
                any(),
                any(Instant.class));
    }

    private static HttpRequest request(String uri) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.PUT);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    private static ClientHttpResponse response(HttpStatus status) {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        try {
            when(response.getStatusCode()).thenReturn(status);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return response;
    }

    private static ClientHttpRequestExecution execution(ClientHttpResponse response) {
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        try {
            when(execution.execute(any(HttpRequest.class), any(byte[].class)))
                    .thenReturn(response);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return execution;
    }

    private void intercept(
            HttpRequest request,
            ClientHttpRequestExecution execution) {
        try {
            interceptor.intercept(request, new byte[0], execution);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
