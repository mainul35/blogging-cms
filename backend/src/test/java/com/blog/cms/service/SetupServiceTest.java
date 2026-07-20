package com.blog.cms.service;

import com.blog.cms.dto.MailSettingsRequest;
import com.blog.cms.dto.MailSettingsResponse;
import com.blog.cms.dto.SetupRequest;
import com.blog.cms.model.SiteSettings;
import com.blog.cms.model.User;
import com.blog.cms.repository.SiteSettingsRepository;
import com.blog.cms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock private SiteSettingsRepository siteSettingsRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailSettingsService mailSettingsService;

    private SetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new SetupService(siteSettingsRepository, userRepository, passwordEncoder, mailSettingsService);
    }

    private SiteSettings settings(boolean completed) {
        return SiteSettings.builder().id(1L).siteName("Blog CMS").setupCompleted(completed).build();
    }

    private User seededAdmin() {
        return User.builder().id(1L).email("admin@blog.com").username("Admin").role("ADMIN").build();
    }

    private SetupRequest request(String email) {
        SetupRequest r = new SetupRequest();
        r.setSiteName("My New Blog");
        r.setAdminName("New Admin");
        r.setAdminEmail(email);
        r.setAdminPassword("brand-new-pass");
        return r;
    }

    // ---- getStatus ----

    @Test
    void getStatus_reportsFlagFromRow() {
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(settings(true)));

        StepVerifier.create(setupService.getStatus())
                .assertNext(r -> assertThat(r.isCompleted()).isTrue())
                .verifyComplete();
    }

    // ---- completeSetup ----

    @Test
    void completeSetup_alreadyCompleted_isForbidden_neverTouchesUser() {
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(settings(true)));

        StepVerifier.create(setupService.completeSetup(request("new@blog.com")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();

        verify(userRepository, never()).findFirstByRole(any());
    }

    @Test
    void completeSetup_noAdminSeeded_isNotFound() {
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(settings(false)));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.empty());

        StepVerifier.create(setupService.completeSetup(request("new@blog.com")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void completeSetup_emailChangedToAlreadyTakenAddress_isConflict() {
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(settings(false)));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.just(seededAdmin()));
        when(userRepository.existsByEmail("taken@blog.com")).thenReturn(Mono.just(true));

        StepVerifier.create(setupService.completeSetup(request("taken@blog.com")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409))
                .verify();

        verify(userRepository, never()).save(any());
        verify(siteSettingsRepository, never()).save(any());
    }

    @Test
    void completeSetup_success_updatesAdminAndFlipsCompletedFlag_noMailSettingsSent() {
        SiteSettings row = settings(false);
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(row));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.just(seededAdmin()));
        when(userRepository.existsByEmail("new@blog.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("brand-new-pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(siteSettingsRepository.save(any(SiteSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(setupService.completeSetup(request("new@blog.com"))).verifyComplete();

        verify(userRepository).save(argThatUser(u -> "new@blog.com".equals(u.getEmail())
                && "hashed".equals(u.getPassword()) && "New Admin".equals(u.getUsername())));
        verify(siteSettingsRepository).save(argThatSettings(s -> s.isSetupCompleted() && "My New Blog".equals(s.getSiteName())));
        verify(mailSettingsService, never()).updateSettings(any());
    }

    @Test
    void completeSetup_emailUnchanged_skipsUniquenessCheck() {
        SiteSettings row = settings(false);
        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(row));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.just(seededAdmin()));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(siteSettingsRepository.save(any(SiteSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(setupService.completeSetup(request("admin@blog.com"))).verifyComplete();

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void completeSetup_withOptionalMailSettings_alsoUpdatesMailSettings() {
        SiteSettings row = settings(false);
        MailSettingsRequest mailRequest = new MailSettingsRequest();
        mailRequest.setProvider("smtp");
        mailRequest.setFromAddress("noreply@blog.com");
        SetupRequest req = request("admin@blog.com");
        req.setMailSettings(mailRequest);

        when(siteSettingsRepository.findById(1L)).thenReturn(Mono.just(row));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.just(seededAdmin()));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(siteSettingsRepository.save(any(SiteSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mailSettingsService.updateSettings(mailRequest)).thenReturn(Mono.just(MailSettingsResponse.builder().build()));

        StepVerifier.create(setupService.completeSetup(req)).verifyComplete();

        verify(mailSettingsService).updateSettings(mailRequest);
    }

    private static User argThatUser(java.util.function.Predicate<User> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    private static SiteSettings argThatSettings(java.util.function.Predicate<SiteSettings> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
