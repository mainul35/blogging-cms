package com.blog.cms.controller;

import com.blog.cms.dto.SiteSettingsRequest;
import com.blog.cms.dto.SiteSettingsResponse;
import com.blog.cms.service.SiteSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteSettingsControllerTest {

    @Mock private SiteSettingsService siteSettingsService;

    private SiteSettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new SiteSettingsController(siteSettingsService);
    }

    @Test
    void getSettings_delegates() {
        SiteSettingsResponse response = SiteSettingsResponse.builder().siteName("My Blog").build();
        when(siteSettingsService.getSettings()).thenReturn(Mono.just(response));

        StepVerifier.create(controller.getSettings()).expectNext(response).verifyComplete();
    }

    @Test
    void updateSettings_delegatesRequest() {
        SiteSettingsRequest request = new SiteSettingsRequest();
        request.setSiteName("New Name");
        request.setTheme("dark");
        request.setContrast("normal");
        request.setFont("inter");
        request.setAccentColor("blue");
        SiteSettingsResponse response = SiteSettingsResponse.builder().siteName("New Name").build();
        when(siteSettingsService.updateSettings(request)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.updateSettings(request)).expectNext(response).verifyComplete();
    }
}
