package com.blog.cms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Thrown by the WebFlux multipart parser while resolving @RequestPart, before
    // any controller/service code runs — so UploadService's own size check never
    // gets a chance to produce a friendlier message for oversized uploads. Logged
    // at WARN since this previously failed silently server-side (no trace at all
    // in blog_backend's own logs) while debugging a real upload failure in prod.
    @ExceptionHandler(DataBufferLimitException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(DataBufferLimitException ex) {
        log.warn("Multipart part exceeded spring.webflux.multipart.max-in-memory-size: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "status", 413,
                        "error", "Payload Too Large",
                        "message", "Uploaded file exceeds the maximum allowed size"
                ));
    }

    // Without this, Spring's default WebFlux error handling drops the reason text
    // (e.g. UploadService's "Image must be 5MB or smaller", AuthService's "Email
    // already in use") from the JSON body entirely -- the client only ever saw
    // {status, error, path, requestId}, so every service-layer validation error
    // looked identical to the frontend and had to be replaced with a generic
    // hardcoded string. Also had no log call at all -- every one of these
    // (wrong content-type on an upload, a duplicate email, an expired reset
    // token, etc.) was as invisible server-side as the DataBufferLimitException
    // case above, for the same reason: found while chasing an upload failure
    // that turned out to have nothing to do with file size.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.warn("{} {}: {}", status.value(), status.getReasonPhrase(), ex.getReason());
        return ResponseEntity.status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()
                ));
    }

    // Catch-all for anything not already handled above (a real bug, an I/O
    // failure writing to disk, etc.) -- without this, an unanticipated
    // exception falls through to Spring's own default WebFlux handler, which
    // logs it but at a level/format that's easy to miss while grepping for
    // this app's own log lines.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", 500,
                        "error", "Internal Server Error",
                        "message", "Something went wrong. Please try again."
                ));
    }
}
