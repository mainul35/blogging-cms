package com.blog.cms.service;

import com.blog.cms.dto.SiteSettingsRequest;
import com.blog.cms.dto.SiteSettingsResponse;
import com.blog.cms.repository.SiteSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SiteSettingsService {

    // Always exactly one row, seeded by V7__create_site_settings.sql.
    private static final Long SETTINGS_ID = 1L;

    private final SiteSettingsRepository repository;

    public Mono<SiteSettingsResponse> getSettings() {
        return repository.findById(SETTINGS_ID)
                .map(settings -> SiteSettingsResponse.builder().siteName(settings.getSiteName()).build());
    }

    public Mono<SiteSettingsResponse> updateSettings(SiteSettingsRequest request) {
        return repository.findById(SETTINGS_ID)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Site settings not found")))
                .flatMap(settings -> {
                    settings.setSiteName(request.getSiteName());
                    return repository.save(settings);
                })
                .map(settings -> SiteSettingsResponse.builder().siteName(settings.getSiteName()).build());
    }
}
