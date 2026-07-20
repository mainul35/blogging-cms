package com.blog.cms.controller;

import com.blog.cms.dto.SetupRequest;
import com.blog.cms.dto.SetupStatusResponse;
import com.blog.cms.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupControllerTest {

    @Mock private SetupService setupService;

    private SetupController controller;

    @BeforeEach
    void setUp() {
        controller = new SetupController(setupService);
    }

    @Test
    void getStatus_delegates() {
        when(setupService.getStatus()).thenReturn(Mono.just(new SetupStatusResponse(true)));

        StepVerifier.create(controller.getStatus())
                .assertNext(r -> assertThat(r.isCompleted()).isTrue())
                .verifyComplete();
    }

    @Test
    void completeSetup_delegatesRequest() {
        SetupRequest request = new SetupRequest();
        request.setSiteName("My Blog");
        request.setAdminName("Admin");
        request.setAdminEmail("admin@blog.com");
        request.setAdminPassword("password123");
        when(setupService.completeSetup(request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.completeSetup(request)).verifyComplete();

        verify(setupService).completeSetup(request);
    }
}
