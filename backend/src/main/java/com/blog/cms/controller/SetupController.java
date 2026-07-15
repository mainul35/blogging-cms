package com.blog.cms.controller;

import com.blog.cms.dto.SetupRequest;
import com.blog.cms.dto.SetupStatusResponse;
import com.blog.cms.service.SetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

// Both endpoints are public: /status must be readable before any admin is
// logged in (that's the whole point), and the POST self-guards via
// SetupService's setup_completed check rather than Spring Security, the same
// pattern AuthController's emergency-reset endpoint uses with its secret.
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public Mono<SetupStatusResponse> getStatus() {
        return setupService.getStatus();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> completeSetup(@RequestBody @Valid SetupRequest request) {
        return setupService.completeSetup(request);
    }
}
