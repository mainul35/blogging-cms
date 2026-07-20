package com.blog.cms.controller;

import com.blog.cms.dto.MediumImportJobState;
import com.blog.cms.dto.MediumImportJobStatusResponse;
import com.blog.cms.dto.MediumImportRequest;
import com.blog.cms.service.MediumImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediumImportControllerTest {

    @Mock private MediumImportService mediumImportService;

    private MediumImportController controller;

    @BeforeEach
    void setUp() {
        controller = new MediumImportController(mediumImportService);
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("admin@blog.com", null, List.of());
    }

    @Test
    void startImport_wrapsJobIdFromService() {
        MediumImportRequest request = new MediumImportRequest();
        request.setFetchUrl("https://medium.com/@x/article-abc123");
        request.setOwnershipConfirmed(true);
        when(mediumImportService.startImportJob(request, "admin@blog.com")).thenReturn(Mono.just("job-42"));

        StepVerifier.create(controller.startImport(request, auth()))
                .assertNext(r -> assertThat(r.getJobId()).isEqualTo("job-42"))
                .verifyComplete();
    }

    @Test
    void getStatus_delegatesJobId() {
        MediumImportJobStatusResponse status = MediumImportJobStatusResponse.builder()
                .jobId("job-42").state(MediumImportJobState.RUNNING).build();
        when(mediumImportService.getJobStatus("job-42")).thenReturn(Mono.just(status));

        StepVerifier.create(controller.getStatus("job-42")).expectNext(status).verifyComplete();
    }
}
