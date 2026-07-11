package com.blog.cms.controller;

import com.blog.cms.dto.SiteSettingsRequest;
import com.blog.cms.dto.SiteSettingsResponse;
import com.blog.cms.service.SiteSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// Split across two prefixes rather than one @RequestMapping: the GET must be public
// (the site name is shown on every public page's header), while the PUT is
// admin-only — covered by SecurityConfig's existing /api/admin/** rule.
@RestController
@RequiredArgsConstructor
public class SiteSettingsController {

    private final SiteSettingsService siteSettingsService;

    @GetMapping("/api/settings")
    public Mono<SiteSettingsResponse> getSettings() {
        return siteSettingsService.getSettings();
    }

    @PutMapping("/api/admin/settings")
    public Mono<SiteSettingsResponse> updateSettings(@RequestBody @Valid SiteSettingsRequest request) {
        return siteSettingsService.updateSettings(request);
    }
}
