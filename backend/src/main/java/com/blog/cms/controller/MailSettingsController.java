package com.blog.cms.controller;

import com.blog.cms.dto.MailConfiguredResponse;
import com.blog.cms.dto.MailSettingsRequest;
import com.blog.cms.dto.MailSettingsResponse;
import com.blog.cms.service.MailSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

// Per-method paths (not a single class-level @RequestMapping) so /status can be
// public while the read/write settings endpoints stay under /api/admin/** ->
// hasRole("ADMIN") in SecurityConfig -- same split as SiteSettingsController.
@RestController
@RequiredArgsConstructor
public class MailSettingsController {

    private final MailSettingsService mailSettingsService;

    // Public: lets the frontend decide whether to show newsletter signup /
    // "forgot password" without ever needing an admin session.
    @GetMapping("/api/mail-settings/status")
    public Mono<MailConfiguredResponse> getPublicStatus() {
        return mailSettingsService.getPublicStatus();
    }

    @GetMapping("/api/admin/mail-settings")
    public Mono<MailSettingsResponse> getSettings() {
        return mailSettingsService.getSettings();
    }

    @PutMapping("/api/admin/mail-settings")
    public Mono<MailSettingsResponse> updateSettings(@RequestBody @Valid MailSettingsRequest request) {
        return mailSettingsService.updateSettings(request);
    }
}
