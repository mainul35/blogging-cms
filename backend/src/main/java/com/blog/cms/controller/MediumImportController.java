package com.blog.cms.controller;

import com.blog.cms.dto.MediumImportRequest;
import com.blog.cms.dto.MediumImportResponse;
import com.blog.cms.service.MediumImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MediumImportResponse> importArticle(@RequestBody @Valid MediumImportRequest request,
                                                      Authentication auth) {
        return mediumImportService.importArticle(request, auth.getName());
    }
}
