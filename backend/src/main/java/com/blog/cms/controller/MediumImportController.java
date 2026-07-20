package com.blog.cms.controller;

import com.blog.cms.dto.MediumImportJobResponse;
import com.blog.cms.dto.MediumImportJobStatusResponse;
import com.blog.cms.dto.MediumImportRequest;
import com.blog.cms.service.MediumImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// Under /api/admin/** -- already gated to hasRole("ADMIN") by SecurityConfig,
// no security wiring needed here.
@RestController
@RequestMapping("/api/admin/medium-import")
@RequiredArgsConstructor
public class MediumImportController {

    private final MediumImportService mediumImportService;

    // 202, not 201/200 -- nothing has been created yet, just a job. The
    // eventual post only exists once GET .../status reports DONE.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<MediumImportJobResponse> startImport(@RequestBody @Valid MediumImportRequest request,
                                                       Authentication auth) {
        return mediumImportService.startImportJob(request, auth.getName())
                .map(MediumImportJobResponse::new);
    }

    @GetMapping("/{jobId}/status")
    public Mono<MediumImportJobStatusResponse> getStatus(@PathVariable String jobId) {
        return mediumImportService.getJobStatus(jobId);
    }
}
