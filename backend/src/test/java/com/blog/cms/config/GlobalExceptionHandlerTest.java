package com.blog.cms.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleTooLarge_returns413WithFriendlyMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleTooLarge(
                new DataBufferLimitException("exceeded 6291456 bytes"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("status", 413);
        assertThat(response.getBody().get("message")).isEqualTo("Uploaded file exceeds the maximum allowed size");
    }

    @Test
    void handleResponseStatus_preservesOriginalStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        assertThat(response.getBody().get("message")).isEqualTo("Email already in use");
    }

    @Test
    void handleResponseStatus_nullReason_fallsBackToReasonPhrase() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getBody().get("message")).isEqualTo("Not Found");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage_neverLeaksExceptionDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(
                new RuntimeException("some internal detail that shouldn't reach the client"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody().get("message")).isEqualTo("Something went wrong. Please try again.");
        assertThat(response.getBody().values())
                .noneMatch(v -> v.toString().contains("some internal detail"));
    }
}
