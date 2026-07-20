package com.blog.cms.service;

import com.blog.cms.dto.MailSettingsRequest;
import com.blog.cms.model.MailSettings;
import com.blog.cms.repository.MailSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSettingsServiceTest {

    @Mock private MailSettingsRepository repository;

    private MailSettingsService service;

    @BeforeEach
    void setUp() {
        service = new MailSettingsService(repository);
    }

    private MailSettings existingLogOnly() {
        return MailSettings.builder().id(1L).provider("log").fromAddress("noreply@blog.com").build();
    }

    private MailSettings existingSmtpWithSecret() {
        return MailSettings.builder().id(1L).provider("smtp").fromAddress("noreply@blog.com")
                .smtpHost("smtp.example.com").smtpPort(587).smtpUsername("user")
                .smtpPassword("super-secret").smtpAuth(true).smtpStarttls(true).build();
    }

    // ---- getSettings ----

    @Test
    void getSettings_hasSecretSet_reportsBooleanFlagNotRawValue() {
        when(repository.findById(1L)).thenReturn(Mono.just(existingSmtpWithSecret()));

        StepVerifier.create(service.getSettings())
                .assertNext(r -> {
                    assertThat(r.isHasSmtpPassword()).isTrue();
                    assertThat(r.isHasResendApiKey()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void getSettings_missingRow_isNotFound() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getSettings())
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    // ---- isMailConfigured / getPublicStatus ----

    @Test
    void isMailConfigured_logProvider_isFalse() {
        when(repository.findById(1L)).thenReturn(Mono.just(existingLogOnly()));

        StepVerifier.create(service.isMailConfigured())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isMailConfigured_nonLogProvider_isTrue() {
        when(repository.findById(1L)).thenReturn(Mono.just(existingSmtpWithSecret()));

        StepVerifier.create(service.isMailConfigured())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isMailConfigured_noRowAtAll_defaultsToFalse() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.isMailConfigured())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void getPublicStatus_wrapsIsMailConfigured() {
        when(repository.findById(1L)).thenReturn(Mono.just(existingSmtpWithSecret()));

        StepVerifier.create(service.getPublicStatus())
                .assertNext(r -> assertThat(r.isConfigured()).isTrue())
                .verifyComplete();
    }

    // ---- updateSettings ----

    @Test
    void updateSettings_missingRow_isNotFound() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateSettings(new MailSettingsRequest()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void updateSettings_blankSecretFields_keepsExistingValues() {
        MailSettingsRequest request = new MailSettingsRequest();
        request.setProvider("smtp");
        request.setFromAddress("new-from@blog.com");
        request.setSmtpPassword("   "); // blank -- should NOT overwrite

        when(repository.findById(1L)).thenReturn(Mono.just(existingSmtpWithSecret()));
        when(repository.save(any(MailSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateSettings(request))
                .assertNext(r -> assertThat(r.isHasSmtpPassword()).isTrue()) // still true -- untouched
                .verifyComplete();

        verify(repository).save(argThat(s -> "super-secret".equals(s.getSmtpPassword())));
    }

    @Test
    void updateSettings_nonBlankSecretField_overwritesExistingValue() {
        MailSettingsRequest request = new MailSettingsRequest();
        request.setProvider("smtp");
        request.setFromAddress("new-from@blog.com");
        request.setSmtpPassword("brand-new-secret");

        when(repository.findById(1L)).thenReturn(Mono.just(existingSmtpWithSecret()));
        when(repository.save(any(MailSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateSettings(request)).expectNextCount(1).verifyComplete();

        verify(repository).save(argThat(s -> "brand-new-secret".equals(s.getSmtpPassword())));
    }

    private static MailSettings argThat(java.util.function.Predicate<MailSettings> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
