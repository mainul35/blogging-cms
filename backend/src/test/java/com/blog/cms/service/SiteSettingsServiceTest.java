package com.blog.cms.service;

import com.blog.cms.dto.SiteSettingsRequest;
import com.blog.cms.model.SiteSettings;
import com.blog.cms.repository.SiteSettingsRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteSettingsServiceTest {

    @Mock private SiteSettingsRepository repository;

    private SiteSettingsService service;

    @BeforeEach
    void setUp() {
        service = new SiteSettingsService(repository);
    }

    private SiteSettings existing() {
        return SiteSettings.builder().id(1L).siteName("Old Name").theme("light")
                .contrast("normal").font("inter").accentColor("blue").setupCompleted(true).build();
    }

    @Test
    void getSettings_mapsRowToResponse() {
        when(repository.findById(1L)).thenReturn(Mono.just(existing()));

        StepVerifier.create(service.getSettings())
                .assertNext(r -> assertThat(r.getSiteName()).isEqualTo("Old Name"))
                .verifyComplete();
    }

    @Test
    void updateSettings_existingRow_savesAllFieldsAndReturnsResponse() {
        SiteSettingsRequest request = new SiteSettingsRequest();
        request.setSiteName("New Name");
        request.setTheme("dark");
        request.setContrast("high");
        request.setFont("serif");
        request.setAccentColor("purple");

        when(repository.findById(1L)).thenReturn(Mono.just(existing()));
        when(repository.save(any(SiteSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateSettings(request))
                .assertNext(r -> {
                    assertThat(r.getSiteName()).isEqualTo("New Name");
                    assertThat(r.getTheme()).isEqualTo("dark");
                    assertThat(r.getContrast()).isEqualTo("high");
                    assertThat(r.getFont()).isEqualTo("serif");
                    assertThat(r.getAccentColor()).isEqualTo("purple");
                })
                .verifyComplete();
    }

    @Test
    void updateSettings_rowMissing_isNotFound() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateSettings(new SiteSettingsRequest()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }
}
