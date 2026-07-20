package com.blog.cms.controller;

import com.blog.cms.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock private UploadService uploadService;
    @Mock private FilePart filePart;

    private UploadController controller;

    @BeforeEach
    void setUp() {
        controller = new UploadController(uploadService);
    }

    @Test
    void upload_wrapsReturnedUrlInMap() {
        when(uploadService.store(filePart)).thenReturn(Mono.just("/uploads/abc123.png"));

        StepVerifier.create(controller.upload(filePart))
                .expectNext(java.util.Map.of("url", "/uploads/abc123.png"))
                .verifyComplete();
    }
}
