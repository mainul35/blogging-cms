package com.blog.cms.service;

import com.blog.cms.dto.MailConfiguredResponse;
import com.blog.cms.dto.MailSettingsRequest;
import com.blog.cms.dto.MailSettingsResponse;
import com.blog.cms.model.MailSettings;
import com.blog.cms.repository.MailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MailSettingsService {

    // Always exactly one row, seeded by V11__create_mail_settings.sql.
    private static final Long SETTINGS_ID = 1L;

    private final MailSettingsRepository repository;

    public Mono<MailSettingsResponse> getSettings() {
        return repository.findById(SETTINGS_ID)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Mail settings not found")))
                .map(this::toResponse);
    }

    // "Configured" just means "not the log-only default" -- doesn't verify the
    // credentials actually work, only that the admin deliberately set something
    // up. Used to gate outward-facing features (newsletter signup, password
    // reset via email) that would otherwise silently do nothing a real visitor
    // could ever see.
    public Mono<Boolean> isMailConfigured() {
        return repository.findById(SETTINGS_ID)
                .map(settings -> !"log".equals(settings.getProvider()))
                .defaultIfEmpty(false);
    }

    public Mono<MailConfiguredResponse> getPublicStatus() {
        return isMailConfigured().map(MailConfiguredResponse::new);
    }

    // Also called directly by SetupService when the setup wizard's optional
    // mail step is filled in -- same "blank secret = keep existing" semantics
    // either way, since it's the exact same method.
    public Mono<MailSettingsResponse> updateSettings(MailSettingsRequest request) {
        return repository.findById(SETTINGS_ID)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Mail settings not found")))
                .flatMap(settings -> {
                    settings.setProvider(request.getProvider());
                    settings.setFromAddress(request.getFromAddress());
                    settings.setReplyTo(request.getReplyTo());
                    settings.setSmtpHost(request.getSmtpHost());
                    settings.setSmtpPort(request.getSmtpPort());
                    settings.setSmtpUsername(request.getSmtpUsername());
                    settings.setSmtpAuth(request.isSmtpAuth());
                    settings.setSmtpStarttls(request.isSmtpStarttls());
                    if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
                        settings.setSmtpPassword(request.getSmtpPassword());
                    }
                    if (request.getResendApiKey() != null && !request.getResendApiKey().isBlank()) {
                        settings.setResendApiKey(request.getResendApiKey());
                    }
                    if (request.getSendgridApiKey() != null && !request.getSendgridApiKey().isBlank()) {
                        settings.setSendgridApiKey(request.getSendgridApiKey());
                    }
                    return repository.save(settings);
                })
                .map(this::toResponse);
    }

    private MailSettingsResponse toResponse(MailSettings s) {
        return MailSettingsResponse.builder()
                .provider(s.getProvider())
                .fromAddress(s.getFromAddress())
                .replyTo(s.getReplyTo())
                .smtpHost(s.getSmtpHost())
                .smtpPort(s.getSmtpPort())
                .smtpUsername(s.getSmtpUsername())
                .smtpAuth(s.isSmtpAuth())
                .smtpStarttls(s.isSmtpStarttls())
                .hasSmtpPassword(s.getSmtpPassword() != null && !s.getSmtpPassword().isBlank())
                .hasResendApiKey(s.getResendApiKey() != null && !s.getResendApiKey().isBlank())
                .hasSendgridApiKey(s.getSendgridApiKey() != null && !s.getSendgridApiKey().isBlank())
                .build();
    }
}
