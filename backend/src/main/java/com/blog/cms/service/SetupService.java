package com.blog.cms.service;

import com.blog.cms.dto.SetupRequest;
import com.blog.cms.dto.SetupStatusResponse;
import com.blog.cms.repository.SiteSettingsRepository;
import com.blog.cms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

// First-run wizard, analogous to WordPress's install.php: lets a fresh
// self-hoster replace the Flyway-seeded admin@blog.com / Admin@1234 defaults
// with their own name/email/password and site name. Gated by the
// setup_completed flag on site_settings so it can only ever run once --
// otherwise re-hitting this endpoint on a live instance would let anyone
// take over the admin account, same class of risk as resetAdminToDefault()
// in AuthService (that one is gated by a secret instead, since it's meant to
// be re-runnable in an emergency).
@Service
@RequiredArgsConstructor
public class SetupService {

    private static final Long SETTINGS_ID = 1L;

    private final SiteSettingsRepository siteSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailSettingsService mailSettingsService;

    public Mono<SetupStatusResponse> getStatus() {
        return siteSettingsRepository.findById(SETTINGS_ID)
                .map(settings -> new SetupStatusResponse(settings.isSetupCompleted()));
    }

    public Mono<Void> completeSetup(SetupRequest request) {
        return siteSettingsRepository.findById(SETTINGS_ID)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Site settings not found")))
                .flatMap(settings -> {
                    if (settings.isSetupCompleted()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Initial setup has already been completed"));
                    }
                    return userRepository.findFirstByRole("ADMIN")
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin account not found")))
                            .flatMap(admin -> {
                                boolean emailChanged = !admin.getEmail().equalsIgnoreCase(request.getAdminEmail());
                                Mono<Boolean> emailTaken = emailChanged
                                        ? userRepository.existsByEmail(request.getAdminEmail())
                                        : Mono.just(false);
                                return emailTaken.flatMap(taken -> {
                                    if (taken) {
                                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use"));
                                    }
                                    admin.setUsername(request.getAdminName());
                                    admin.setEmail(request.getAdminEmail());
                                    admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
                                    return userRepository.save(admin);
                                });
                            })
                            .then(Mono.defer(() -> {
                                settings.setSiteName(request.getSiteName());
                                settings.setSetupCompleted(true);
                                return siteSettingsRepository.save(settings);
                            }))
                            .then(Mono.defer(() -> request.getMailSettings() != null
                                    ? mailSettingsService.updateSettings(request.getMailSettings()).then()
                                    : Mono.empty()));
                })
                .then();
    }
}
