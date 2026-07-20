package com.blog.cms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Real filesystem I/O against a JUnit @TempDir rather than mocking
// java.nio.file.Files (static methods aren't mockable without extra
// tooling) -- this exercises UploadService's actual disk behavior, not just
// its validation branches.
@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @TempDir Path tempDir;

    @Mock private FilePart filePart;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService();
        ReflectionTestUtils.setField(uploadService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(uploadService, "allowedTypes", List.of("image/png", "image/jpeg"));
        ReflectionTestUtils.setField(uploadService, "maxSizeMb", 5L);
    }

    private void stubFilePart(String contentType, long contentLength) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(contentLength);
        lenient().when(filePart.headers()).thenReturn(headers);
    }

    // ---- store(FilePart) ----

    @Test
    void storeFilePart_allowedTypeAndSize_returnsUploadUrlAndTransfersFile() {
        stubFilePart("image/png", 1024);
        when(filePart.transferTo(any(Path.class))).thenReturn(Mono.empty());

        StepVerifier.create(uploadService.store(filePart))
                .assertNext(url -> {
                    assertThat(url).startsWith("/uploads/");
                    assertThat(url).endsWith(".png");
                })
                .verifyComplete();

        verify(filePart).transferTo(any(Path.class));
    }

    @Test
    void storeFilePart_disallowedType_isBadRequest_neverTransfers() {
        stubFilePart("image/svg+xml", 1024);

        StepVerifier.create(uploadService.store(filePart))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400))
                .verify();

        verify(filePart, never()).transferTo(any(Path.class));
    }

    @Test
    void storeFilePart_tooLarge_isPayloadTooLarge() {
        stubFilePart("image/png", 6L * 1024 * 1024); // 6MB > 5MB limit

        StepVerifier.create(uploadService.store(filePart))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(413))
                .verify();
    }

    // ---- store(byte[], contentType) ----

    @Test
    void storeBytes_allowedTypeAndSize_writesRealFileToUploadDir() {
        byte[] data = "fake-png-bytes".getBytes();

        StepVerifier.create(uploadService.store(data, "image/jpeg"))
                .assertNext(url -> {
                    assertThat(url).startsWith("/uploads/").endsWith(".jpg");
                    String filename = url.substring("/uploads/".length());
                    Path written = tempDir.resolve(filename);
                    assertThat(Files.exists(written)).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void storeBytes_disallowedType_isBadRequest_writesNothing() {
        byte[] data = "data".getBytes();

        StepVerifier.create(uploadService.store(data, "application/pdf"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400))
                .verify();
    }

    // ---- extractLocalFilenames ----

    @Test
    void extractLocalFilenames_findsMarkdownImagesAndCoverImage_ignoresExternalAndTraversal() {
        String content = """
                ![alt](/uploads/local-one.png)
                ![alt2](https://example.com/external.png)
                ![alt3](/uploads/sub/nested.png)
                ![alt4](/uploads/../../etc/passwd)
                """;

        Set<String> result = uploadService.extractLocalFilenames(content, "/uploads/cover-image.jpg");

        assertThat(result).containsExactlyInAnyOrder("local-one.png", "cover-image.jpg");
    }

    @Test
    void extractLocalFilenames_nullContentAndCover_returnsEmptySet() {
        assertThat(uploadService.extractLocalFilenames(null, null)).isEmpty();
    }

    // ---- deleteFiles ----

    @Test
    void deleteFiles_emptySet_isNoOp() {
        StepVerifier.create(uploadService.deleteFiles(Set.of())).verifyComplete();
    }

    @Test
    void deleteFiles_existingFile_isRemoved() throws Exception {
        Path file = tempDir.resolve("to-delete.png");
        Files.writeString(file, "content");
        assertThat(Files.exists(file)).isTrue();

        StepVerifier.create(uploadService.deleteFiles(Set.of("to-delete.png"))).verifyComplete();

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteFiles_missingFile_isSilentlyIgnored() {
        StepVerifier.create(uploadService.deleteFiles(Set.of("never-existed.png"))).verifyComplete();
    }

    @Test
    void deleteFiles_pathTraversalFilename_isSkippedSafely_doesNotEscapeUploadDir() throws Exception {
        // A sibling file outside tempDir that a naive resolve+delete could
        // reach via "..\..\<sibling>" if the startsWith(base) guard in
        // deleteFiles were missing or broken.
        Path outside = tempDir.getParent().resolve("outside-upload-dir-should-survive.txt");
        Files.writeString(outside, "must survive");

        try {
            StepVerifier.create(uploadService.deleteFiles(Set.of("../outside-upload-dir-should-survive.txt")))
                    .verifyComplete();

            assertThat(Files.exists(outside)).isTrue();
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
