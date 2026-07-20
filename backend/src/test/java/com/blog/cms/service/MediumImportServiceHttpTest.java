package com.blog.cms.service;

import com.blog.cms.dto.MediumImportJobState;
import com.blog.cms.dto.MediumImportJobStatusResponse;
import com.blog.cms.model.Post;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Exercises MediumImportService.runImportJob's real network-facing logic
// (fetchArticlePage, downloadAndStoreImage, and everything they feed into)
// against a fake ExchangeFunction instead of a real Netty connector -- see
// redirectFollowingWebClient's javadoc-style comment for why this is safe:
// the request URIs are still genuine medium.com/miro.medium.com hosts (the
// SSRF allowlist in validateFetchUrl is never touched, and isn't exercised
// by this test class -- MediumImportServiceTest covers that), only the
// actual HTTP dispatch is intercepted. Real Netty redirect-following
// behavior itself isn't exercised this way (the fake ExchangeFunction
// returns the final response directly, no 301 hop) -- that's Reactor
// Netty's own tested behavior, not this codebase's logic.
@ExtendWith(MockitoExtension.class)
class MediumImportServiceHttpTest {

    private static final String ARTICLE_HOST = "mainul35.medium.com";
    private static final String POST_ID = "d6aa51936735";
    private static final URI FETCH_URI = URI.create("https://" + ARTICLE_HOST + "/complete-guide-" + POST_ID);

    // Same fixture shape as MediumArticleConverterTest (confirmed against a
    // real Medium article fetch) -- one heading, one text paragraph, one
    // inline image, and a cover image, which is enough to exercise both the
    // article-parsing path and the image-download path in one fixture.
    private static final String FIXTURE_HTML = """
            <!doctype html><html><head><title>Test</title></head><body>
            <div id="root"></div>
            <script>window.__APOLLO_STATE__ = {"Post:%s":{
              "__typename":"Post",
              "title":"Complete Guide",
              "extendedPreviewContent":{"__typename":"PreviewContent","subtitle":"A subtitle"},
              "previewImage":{"__typename":"ImageMetadata","id":"1*cover.png"},
              "content({\\"postMeteringOptions\\":{\\"referrer\\":\\"\\"}})":{
                "__typename":"PostContent",
                "bodyModel":{"__typename":"RichText","paragraphs":[
                  {"__ref":"Paragraph:p_0"},
                  {"__ref":"Paragraph:p_1"},
                  {"__ref":"Paragraph:p_2"}
                ]}
              }
            },
            "Paragraph:p_0":{"__typename":"Paragraph","type":"H3","text":"Heading","markups":[]},
            "Paragraph:p_1":{"__typename":"Paragraph","type":"P","text":"Some body text.","markups":[]},
            "Paragraph:p_2":{"__typename":"Paragraph","type":"IMG","text":"","metadata":{"__typename":"ImageMetadata","id":"1*inline.png"}}
            };</script>
            </body></html>""".formatted(POST_ID);

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UploadService uploadService;
    @Mock private ReactiveRedisTemplate<String, Object> redisTemplate;
    @Mock private ReactiveValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.set(anyString(), any(MediumImportJobStatusResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));
        lenient().when(postRepository.findBySlug(anyString())).thenReturn(Mono.empty()); // no slug collision
    }

    private MediumImportService serviceWithFakeExchange(ExchangeFunction exchangeFunction) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new MediumImportService(postRepository, userRepository, uploadService, redisTemplate, objectMapper) {
            @Override
            protected WebClient redirectFollowingWebClient() {
                return WebClient.builder().exchangeFunction(exchangeFunction).build();
            }
        };
    }

    private ExchangeFunction routingBy(java.util.function.Function<ClientRequestSnapshot, Mono<ClientResponse>> router) {
        return request -> router.apply(new ClientRequestSnapshot(request.url().getHost(), request.url().getPath()));
    }

    private record ClientRequestSnapshot(String host, String path) { }

    @Test
    void runImportJob_realArticleAndImages_extractsConvertsAndWritesDoneStatus() {
        ExchangeFunction fakeExchange = routingBy(req -> {
            if (ARTICLE_HOST.equals(req.host())) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "text/html").body(FIXTURE_HTML).build());
            }
            if ("miro.medium.com".equals(req.host())) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "image/png").body("fake-image-bytes").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        });
        MediumImportService service = serviceWithFakeExchange(fakeExchange);

        when(uploadService.store(any(byte[].class), eq("image/png"))).thenReturn(Mono.just("/uploads/generated.png"));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(100L);
            return Mono.just(p);
        });

        StepVerifier.create(service.runImportJob("job-1", FETCH_URI, POST_ID, 1L)).verifyComplete();

        verify(valueOperations).set(eq("medium-import-job:job-1"), argThatStatus(status ->
                status.getState() == MediumImportJobState.DONE
                        && status.getTitle().equals("Complete Guide")
                        && status.getPostId().equals(100L)
                        && status.getImagesImported() == 2   // cover + inline
                        && status.getImagesFailed() == 0
        ), any(Duration.class));
        verify(postRepository).save(argThat(p ->
                p.getContent().contains("Some body text.") && p.getContent().contains("/uploads/generated.png")));
    }

    @Test
    void runImportJob_imageCdnFails_stillCompletesWithImagesFailedCounted() {
        ExchangeFunction fakeExchange = routingBy(req -> {
            if (ARTICLE_HOST.equals(req.host())) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "text/html").body(FIXTURE_HTML).build());
            }
            // Every image request fails -- downloadAndStoreImage's own
            // onErrorResume must swallow this per-image, not fail the job.
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        });
        MediumImportService service = serviceWithFakeExchange(fakeExchange);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(101L);
            return Mono.just(p);
        });

        StepVerifier.create(service.runImportJob("job-2", FETCH_URI, POST_ID, 1L)).verifyComplete();

        // Only 1, not 2: buildPost's cover-image path increments
        // imagesImported on success but never imagesFailed on failure (it
        // just silently omits the cover via coverImageMono.defaultIfEmpty("")).
        // Only the per-paragraph inline-image path in resolveBlockMarkdown
        // increments imagesFailed, via its own explicit switchIfEmpty. Real,
        // pre-existing asymmetry -- this test caught it, not introduced it.
        verify(valueOperations).set(eq("medium-import-job:job-2"), argThatStatus(status ->
                status.getState() == MediumImportJobState.DONE
                        && status.getImagesImported() == 0
                        && status.getImagesFailed() == 1
        ), any(Duration.class));
        verify(uploadService, org.mockito.Mockito.never()).store(any(byte[].class), anyString());
        verify(postRepository).save(argThat(p -> p.getContent().contains("[Image could not be imported]")));
    }

    @Test
    void runImportJob_articleFetchFails_writesFailedStatus_neverSavesPost() {
        ExchangeFunction fakeExchange = routingBy(req -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));
        MediumImportService service = serviceWithFakeExchange(fakeExchange);

        StepVerifier.create(service.runImportJob("job-3", FETCH_URI, POST_ID, 1L)).verifyComplete();

        verify(valueOperations).set(eq("medium-import-job:job-3"), argThatStatus(status ->
                status.getState() == MediumImportJobState.FAILED && status.getErrorMessage() != null
        ), any(Duration.class));
        verify(postRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void runImportJob_postIdNotInApolloState_writesFailedStatus() {
        ExchangeFunction fakeExchange = routingBy(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/html").body(FIXTURE_HTML).build()));
        MediumImportService service = serviceWithFakeExchange(fakeExchange);

        // A postId that doesn't match "Post:<id>" in the fixture's Apollo state.
        StepVerifier.create(service.runImportJob("job-4", FETCH_URI, "doesnotexist", 1L)).verifyComplete();

        verify(valueOperations).set(eq("medium-import-job:job-4"), argThatStatus(status ->
                status.getState() == MediumImportJobState.FAILED
        ), any(Duration.class));
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    // Separate, explicitly-typed helper for the Redis status matcher: T can't
    // be inferred from context there, since ReactiveValueOperations<String,
    // Object>.set(K, V, Duration) types its value parameter as plain Object,
    // not MediumImportJobStatusResponse -- generic argThat(...) above would
    // silently infer T=Object and let the lambda's status.getState() etc.
    // fail to compile against Object.
    private static MediumImportJobStatusResponse argThatStatus(
            java.util.function.Predicate<MediumImportJobStatusResponse> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
