package com.blog.cms.service;

import com.blog.cms.dto.MediumImportJobState;
import com.blog.cms.dto.MediumImportJobStatusResponse;
import com.blog.cms.dto.MediumImportRequest;
import com.blog.cms.model.User;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// NOTE on scope: this class covers only the fast, synchronous branches of
// startImportJob (URL/host validation, rate limiting, author lookup) and
// getJobStatus (a plain Redis read) -- none of it subscribes to the returned
// Mono where doing so would trigger the real detached runImportJob()
// .subscribe() call. The actual network-facing "happy path" (article fetch,
// image downloads, and everything downstream of them) is covered separately
// in MediumImportServiceHttpTest, which calls package-private runImportJob(...)
// directly against a fake ExchangeFunction (redirectFollowingWebClient() was
// widened from private to protected specifically to make that overridable in
// a test subclass -- see that class's own comment for the full design). The
// pure parsing/conversion logic fetchArticlePage/buildPost call is also
// covered independently by MediumArticleConverterTest.
@ExtendWith(MockitoExtension.class)
class MediumImportServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UploadService uploadService;
    @Mock private ReactiveRedisTemplate<String, Object> redisTemplate;
    @Mock private ReactiveValueOperations<String, Object> valueOperations;

    private MediumImportService service;

    private static final String AUTHOR_EMAIL = "admin@blog.com";
    private static final String VALID_ARTICLE_URL =
            "https://mainul35.medium.com/some-article-title-d6aa51936735";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new MediumImportService(postRepository, userRepository, uploadService,
                redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private MediumImportRequest requestFor(String url) {
        MediumImportRequest request = new MediumImportRequest();
        request.setFetchUrl(url);
        request.setOwnershipConfirmed(true);
        return request;
    }

    // ---- URL / SSRF-host validation (thrown synchronously, before any Mono exists) ----

    @ParameterizedTest
    @ValueSource(strings = {
            "http://mainul35.medium.com/x",              // not https
            "https://medium.com.evil.com/x",              // suffix-spoofing host
            "https://evil.com/x",                          // unrelated host
            "not a url at all",
    })
    void startImportJob_rejectsUrlsFailingHostOrSchemeValidation(String badUrl) {
        assertThatThrownBy(() -> service.startImportJob(requestFor(badUrl), AUTHOR_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void startImportJob_acceptsMediumDotComHost_doesNotThrowOnValidation() {
        // isWithinRateLimit's redisTemplate.opsForValue().increment(key) call
        // is invoked eagerly while startImportJob's method body constructs
        // the .flatMap() chain -- that's an ordinary Java method call, not a
        // deferred Reactor operation, so it needs a real stub even though
        // nothing ever subscribes to the Mono this test builds (confirmed by
        // this test itself: it NPE'd on an unstubbed mock before this line
        // was added). The nested closure inside .flatMap(author -> ...) that
        // fires the detached runImportJob().subscribe() network call, by
        // contrast, genuinely never runs here, since only subscribing to the
        // returned Mono reaches that point -- which this test deliberately
        // never does. See class-level note.
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));

        assertThatCode(() -> service.startImportJob(requestFor("https://medium.com/@someone/article-abc123"),
                AUTHOR_EMAIL)).doesNotThrowAnyException();
    }

    // ---- rate limiting ----

    @Test
    void startImportJob_rateLimitExceeded_isTooManyRequests_neverLooksUpAuthor() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(11L));

        Mono<String> result = service.startImportJob(requestFor(VALID_ARTICLE_URL), AUTHOR_EMAIL);

        StepVerifier.create(result)
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(429))
                .verify();

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void startImportJob_redisRateLimitUnavailable_failsOpen_proceedsToAuthorLookup() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));
        // Author intentionally missing so the chain still errors out (401)
        // before ever reaching the job-creation / network-triggering branch.
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Mono.empty());

        StepVerifier.create(service.startImportJob(requestFor(VALID_ARTICLE_URL), AUTHOR_EMAIL))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();
    }

    // ---- author lookup ----

    @Test
    void startImportJob_unknownAuthor_isUnauthorized() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Mono.empty());

        StepVerifier.create(service.startImportJob(requestFor(VALID_ARTICLE_URL), AUTHOR_EMAIL))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();
    }

    // ---- getJobStatus ----

    @Test
    void getJobStatus_existingJob_returnsStoredStatus() {
        MediumImportJobStatusResponse stored = MediumImportJobStatusResponse.builder()
                .jobId("abc-123")
                .state(MediumImportJobState.RUNNING)
                .build();
        when(valueOperations.get("medium-import-job:abc-123")).thenReturn(Mono.just(stored));

        StepVerifier.create(service.getJobStatus("abc-123"))
                .assertNext(status -> {
                    assertThat(status.getJobId()).isEqualTo("abc-123");
                    assertThat(status.getState()).isEqualTo(MediumImportJobState.RUNNING);
                })
                .verifyComplete();
    }

    @Test
    void getJobStatus_unknownOrExpiredJob_isNotFound() {
        when(valueOperations.get("medium-import-job:missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.getJobStatus("missing"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }
}
