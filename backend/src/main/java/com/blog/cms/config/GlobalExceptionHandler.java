package com.blog.cms.config;

import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Thrown by the WebFlux multipart parser while resolving @RequestPart, before
    // any controller/service code runs — so UploadService's own size check never
    // gets a chance to produce a friendlier message for oversized uploads.
    @ExceptionHandler(DataBufferLimitException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(DataBufferLimitException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "status", 413,
                        "error", "Payload Too Large",
                        "message", "Uploaded file exceeds the maximum allowed size"
                ));
    }
}
