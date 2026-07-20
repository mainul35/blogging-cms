package com.blog.cms.controller;

import com.blog.cms.dto.MailConfiguredResponse;
import com.blog.cms.dto.MailSettingsRequest;
import com.blog.cms.dto.MailSettingsResponse;
import com.blog.cms.service.MailSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSettingsControllerTest {

    @Mock private MailSettingsService mailSettingsService;

    private MailSettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new MailSettingsController(mailSettingsService);
    }

    @Test
    void getPublicStatus_delegates() {
        when(mailSettingsService.getPublicStatus()).thenReturn(Mono.just(new MailConfiguredResponse(true)));

        StepVerifier.create(controller.getPublicStatus())
                .assertNext(r -> assertThat(r.isConfigured()).isTrue())
                .verifyComplete();
    }

    @Test
    void getSettings_delegates() {
        MailSettingsResponse response = MailSettingsResponse.builder().provider("smtp").build();
        when(mailSettingsService.getSettings()).thenReturn(Mono.just(response));

        StepVerifier.create(controller.getSettings()).expectNext(response).verifyComplete();
    }

    @Test
    void updateSettings_delegatesRequest() {
        MailSettingsRequest request = new MailSettingsRequest();
        request.setProvider("smtp");
        MailSettingsResponse response = MailSettingsResponse.builder().provider("smtp").build();
        when(mailSettingsService.updateSettings(request)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.updateSettings(request)).expectNext(response).verifyComplete();
    }
}
