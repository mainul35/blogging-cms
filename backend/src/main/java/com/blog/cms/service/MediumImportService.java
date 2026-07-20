package com.blog.cms.service;

import com.blog.cms.dto.MediumImportJobState;
import com.blog.cms.dto.MediumImportJobStatusResponse;
import com.blog.cms.dto.MediumImportRequest;
import com.blog.cms.dto.MediumImportResponse;
import com.blog.cms.medium.MediumArticleConverter;
import com.blog.cms.medium.MediumArticleConverter.ParagraphBlock;
import com.blog.cms.model.Post;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediumImportService {

    // Imports are a manual, occasional admin action (not a high-frequency
    // endpoint), but each call makes real outbound network requests and disk
    // writes -- generous but bounded, same reasoning as the other Redis-backed
    // limiters in AuthService.
    private static final int IMPORT_MAX_REQUESTS = 10;
    private static final Duration IMPORT_RATE_WINDOW = Duration.ofHours(1);

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UploadService uploadService;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Async job model (replaces an earlier synchronous version that returned
    // the finished post directly): the fetch-article + N-image-download chain
    // could take well over a minute for an image-heavy article, and holding
    // one HTTP request open through Cloudflare for that whole duration meant
    // racing Cloudflare's own ~100s free/pro-tier edge timeout (not raisable
    // from the dashboard on that plan) -- a race the backend sometimes lost,
    // showing Cloudflare's bare generic 502 page instead of any message this
    // app controls. See troubleshooting.md's "Medium import 502" entries for
    // the two prior attempts (raising the buffer, then a backend-side
    // timeout) that only mitigated this, not fixed it structurally.
    //
    // Now: POST creates a job and returns its id immediately (sub-second);
    // the actual import runs detached (see startImportJob's .subscribe()) and
    // writes its progress to Redis; the frontend polls GET .../{jobId}/status
    // every couple seconds instead of holding one long connection open. Each
    // individual HTTP call is now fast, so Cloudflare's edge timeout stops
    // being relevant to this feature at all -- no more number to tune.
    private static final Duration JOB_TTL = Duration.ofMinutes(30);
    private static final String JOB_KEY_PREFIX = "medium-import-job:";

    // Purely defensive now (not racing anything) -- catches a genuinely wedged
    // job (e.g. Medium's CDN hanging past its own per-call timeouts in some
    // way not already handled) so it doesn't sit in RUNNING forever instead
    // of ever reaching a terminal state.
    private static final Duration JOB_SAFETY_TIMEOUT = Duration.ofMinutes(5);

    // Bounded concurrency for image downloads (see resolveBody below) -- caps
    // miro.medium.com at this many simultaneous requests, not a hammering burst.
    private static final int IMAGE_DOWNLOAD_CONCURRENCY = 3;

    // Fast path only: validates the URL, checks the rate limit, and looks up
    // the author -- all fail-fast, in-request checks -- then hands off the
    // slow work (runImportJob) to a detached subscription and returns the new
    // job's id without waiting for it. A bad URL, a rate limit, or a missing
    // author all still fail synchronously with the same 400/429/401 as
    // before; only the genuinely slow, best-effort part is now async.
    public Mono<String> startImportJob(MediumImportRequest request, String authorEmail) {
        URI fetchUri = validateFetchUrl(request.getFetchUrl());
        String postId = extractPostId(fetchUri);

        return isWithinRateLimit(authorEmail)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS, "Too many Medium imports -- try again later"));
                    }
                    return userRepository.findByEmail(authorEmail)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED, "Author not found")));
                })
                .flatMap(author -> {
                    String jobId = UUID.randomUUID().toString();
                    return writeJobStatus(jobId, MediumImportJobStatusResponse.builder()
                                    .jobId(jobId)
                                    .state(MediumImportJobState.PENDING)
                                    .build())
                            .doOnSuccess(v -> runImportJob(jobId, fetchUri, postId, author.getId())
                                    // Detached on purpose: this method's own Mono
                                    // (returned to the controller) completes as
                                    // soon as the PENDING status is written, not
                                    // when the import finishes. Errors here are
                                    // a bug, not a normal outcome -- runImportJob
                                    // itself already turns every expected failure
                                    // into a written FAILED status via
                                    // onErrorResume before this subscribe ever
                                    // sees an error signal.
                                    .subscribe(
                                            v2 -> { },
                                            e -> log.error("Unhandled error escaped runImportJob for job {}",
                                                    jobId, e)))
                            .thenReturn(jobId);
                });
    }

    public Mono<MediumImportJobStatusResponse> getJobStatus(String jobId) {
        return redisTemplate.opsForValue().get(JOB_KEY_PREFIX + jobId)
                .cast(MediumImportJobStatusResponse.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Import job not found -- it may have expired (jobs are kept " + JOB_TTL.toMinutes()
                                + " minutes) or never existed")));
    }

    private Mono<Void> writeJobStatus(String jobId, MediumImportJobStatusResponse status) {
        return redisTemplate.opsForValue().set(JOB_KEY_PREFIX + jobId, status, JOB_TTL).then();
    }

    // Package-private (not private) purely for testability: lets a same-package
    // test call this directly and await it with StepVerifier instead of racing
    // startImportJob's detached .subscribe(). No behavior change.
    Mono<Void> runImportJob(String jobId, URI fetchUri, String postId, Long authorId) {
        Instant startedAt = Instant.now();
        return writeJobStatus(jobId, MediumImportJobStatusResponse.builder()
                        .jobId(jobId).state(MediumImportJobState.RUNNING).build())
                .then(fetchArticlePage(fetchUri))
                .flatMap(apolloState -> buildPost(apolloState, postId, authorId))
                .timeout(JOB_SAFETY_TIMEOUT)
                .flatMap(result -> writeJobStatus(jobId, MediumImportJobStatusResponse.builder()
                        .jobId(jobId)
                        .state(MediumImportJobState.DONE)
                        .postId(result.getPostId())
                        .slug(result.getSlug())
                        .title(result.getTitle())
                        .imagesImported(result.getImagesImported())
                        .imagesFailed(result.getImagesFailed())
                        .warnings(result.getWarnings())
                        .build()))
                .onErrorResume(e -> {
                    String message = (e instanceof ResponseStatusException rse)
                            ? rse.getReason()
                            : "Import failed unexpectedly -- check docker logs blog_backend";
                    log.warn("Medium import job {} failed: {}", jobId, message, e);
                    return writeJobStatus(jobId, MediumImportJobStatusResponse.builder()
                            .jobId(jobId)
                            .state(MediumImportJobState.FAILED)
                            .errorMessage(message)
                            .build());
                })
                .doOnSuccess(v -> log.info("Medium import job {} reached a terminal state in {}ms", jobId,
                        Duration.between(startedAt, Instant.now()).toMillis()));
    }

    private String extractPostId(URI fetchUri) {
        try {
            return MediumArticleConverter.extractPostIdFromUrl(fetchUri.toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // Runs before any network call. This endpoint makes the server perform an
    // admin-supplied outbound GET -- without restricting the host, it's an
    // SSRF hole (could be pointed at internal services or a cloud metadata
    // endpoint). Deliberately checks the exact host or a *.medium.com suffix,
    // not a substring/contains check, to reject suffix-spoofing hosts like
    // medium.com.evil.com.
    private URI validateFetchUrl(String rawUrl) {
        String normalized = MediumArticleConverter.normalizeFetchUrl(rawUrl);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fetch URL");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || !MediumArticleConverter.isAllowedFetchHost(host)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fetch URL must be an https:// medium.com URL");
        }
        return uri;
    }

    private Mono<Boolean> isWithinRateLimit(String authorEmail) {
        String key = "medium-import-rate:" + authorEmail.toLowerCase();
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redisTemplate.expire(key, IMPORT_RATE_WINDOW).thenReturn(true)
                        : Mono.just(count <= IMPORT_MAX_REQUESTS))
                .onErrorResume(e -> {
                    log.warn("Rate-limit check failed for medium-import, allowing request: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    // Plain unauthenticated GET, no cookies -- v1 only supports articles
    // Medium serves publicly (verified against a real article: the full body
    // was present in the response with no authentication at all). The "fetch
    // URL" an admin captures from DevTools turns out to be the article's own
    // page, not a JSON API endpoint -- so this fetches HTML and extracts the
    // embedded Apollo Client state, rather than parsing the response body as
    // JSON directly. Same inline-per-call WebClient pattern as
    // ResendMailSender/SendGridMailSender (no shared bean exists in this
    // codebase for outbound HTTP).
    private Mono<JsonNode> fetchArticlePage(URI fetchUri) {
        WebClient webClient = redirectFollowingWebClient();
        Instant startedAt = Instant.now();
        return webClient.get()
                .uri(fetchUri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                // Logged before any parsing is attempted -- if maxInMemorySize
                // (10MB, see redirectFollowingWebClient below) is ever too small
                // again for some future article, this line makes that instantly
                // obvious in docker logs blog_backend instead of needing to
                // re-derive it from a bare Cloudflare 502 the way the original
                // incident required.
                .doOnNext(html -> log.info("Fetched Medium article page: {} chars in {}ms",
                        html.length(), Duration.between(startedAt, Instant.now()).toMillis()))
                .onErrorMap(e -> !(e instanceof ResponseStatusException), e ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Could not fetch article from Medium: " + e.getMessage()))
                .flatMap(html -> {
                    try {
                        String json = MediumArticleConverter.extractApolloStateJson(html);
                        return Mono.just(objectMapper.readTree(json));
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
                    } catch (Exception e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Unexpected page structure from Medium -- Medium may have changed how "
                                        + "it renders articles"));
                    }
                });
    }

    private Mono<MediumImportResponse> buildPost(JsonNode apolloState, String postId, Long authorId) {
        JsonNode post;
        JsonNode content;
        try {
            post = MediumArticleConverter.findPost(apolloState, postId);
            content = MediumArticleConverter.findContent(post);
        } catch (IllegalArgumentException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
        }

        String title = post.path("title").asText("Untitled");
        String rawExcerpt = post.path("extendedPreviewContent").path("subtitle").asText(null);
        if (rawExcerpt == null || rawExcerpt.isBlank()) {
            rawExcerpt = post.path("previewContent").path("subtitle").asText(null);
        }
        String excerpt = (rawExcerpt == null || rawExcerpt.isBlank()) ? null : rawExcerpt;
        String previewImageId = post.path("previewImage").path("id").asText(null);

        List<String> warnings = new ArrayList<>();
        JsonNode paragraphRefs = content.path("bodyModel").path("paragraphs");
        List<JsonNode> resolvedParagraphs = MediumArticleConverter.resolveParagraphRefs(
                paragraphRefs, apolloState, warnings);
        List<ParagraphBlock> blocks = MediumArticleConverter.stripDuplicateTitleHeading(
                MediumArticleConverter.parseParagraphs(resolvedParagraphs, warnings), title);

        AtomicInteger imagesImported = new AtomicInteger();
        AtomicInteger imagesFailed = new AtomicInteger();

        Mono<String> coverImageMono = (previewImageId == null || previewImageId.isBlank())
                ? Mono.<String>empty()
                : downloadAndStoreImage(previewImageId).doOnNext(url -> imagesImported.incrementAndGet());

        return coverImageMono.defaultIfEmpty("")
                .flatMap(coverUrl -> resolveBody(blocks, imagesImported, imagesFailed)
                        .flatMap(markdown -> saveDraft(title, excerpt,
                                coverUrl.isBlank() ? null : coverUrl, markdown, authorId))
                        .map(savedPost -> MediumImportResponse.builder()
                                .postId(savedPost.getId())
                                .slug(savedPost.getSlug())
                                .title(savedPost.getTitle())
                                .imagesImported(imagesImported.get())
                                .imagesFailed(imagesFailed.get())
                                .warnings(warnings)
                                .build()));
    }

    private record ResolvedBlock(String type, String markdown) {
    }

    // Bounded concurrency (flatMapSequential, not a fully sequential concatMap)
    // cuts total wall-clock time for image-heavy articles, which is what
    // actually determines whether the whole import finishes inside
    // JOB_SAFETY_TIMEOUT above. flatMapSequential (unlike plain flatMap) still
    // *emits downstream* in the original block order even though downloads
    // run concurrently -- but that guarantee only applies to what's collected
    // from the operator itself. An earlier version of this method instead
    // appended to blockMarkdown/blockTypes from a doOnNext on each inner
    // Mono, which fires the moment that block's own async work (e.g. an image
    // download) completes -- i.e. in real-world completion order, not
    // emission order. With concurrency 3, a fast text block queued behind a
    // slow in-flight image download would finish first and jump the queue,
    // and two images racing each other could land in either order -- exactly
    // the intermittent Fig-N-before-Fig-(N-1) reordering seen in production.
    // Collecting the operator's own ordered output via collectList() instead
    // of relying on side effects is what actually honors the ordering
    // guarantee.
    private Mono<String> resolveBody(List<ParagraphBlock> blocks, AtomicInteger imagesImported,
                                      AtomicInteger imagesFailed) {
        Instant startedAt = Instant.now();

        return Flux.fromIterable(blocks)
                .flatMapSequential(block -> resolveBlockMarkdown(block, imagesImported, imagesFailed)
                        .map(md -> new ResolvedBlock(block.type(), md)), IMAGE_DOWNLOAD_CONCURRENCY)
                .collectList()
                .map(resolved -> {
                    log.info("Resolved {} blocks ({} images imported, {} failed) in {}ms",
                            blocks.size(), imagesImported.get(), imagesFailed.get(),
                            Duration.between(startedAt, Instant.now()).toMillis());
                    List<String> blockMarkdown = resolved.stream().map(ResolvedBlock::markdown).toList();
                    List<String> blockTypes = resolved.stream().map(ResolvedBlock::type).toList();
                    return MediumArticleConverter.assembleMarkdown(blockMarkdown, blockTypes);
                });
    }

    private Mono<String> resolveBlockMarkdown(ParagraphBlock block, AtomicInteger imagesImported,
                                               AtomicInteger imagesFailed) {
        if ("IMG".equals(block.type())) {
            return downloadAndStoreImage(block.imageId())
                    .doOnNext(url -> imagesImported.incrementAndGet())
                    .map(url -> MediumArticleConverter.imageMarkdownWithCaption(url, block.renderedText()))
                    .switchIfEmpty(Mono.fromCallable(() -> {
                        imagesFailed.incrementAndGet();
                        return "*[Image could not be imported]*";
                    }));
        }
        return Mono.justOrEmpty(MediumArticleConverter.markdownFor(block)).defaultIfEmpty("");
    }

    // Never fails the caller -- an individual broken/unreachable image must
    // not abort the whole import (same best-effort convention as
    // UploadService.deleteFiles). Empty result means "no image": the caller
    // substitutes a placeholder note instead.
    private Mono<String> downloadAndStoreImage(String mediumImageId) {
        String cdnUrl = MediumArticleConverter.imageCdnUrl(mediumImageId);
        return redirectFollowingWebClient().get()
                .uri(cdnUrl)
                .retrieve()
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(15))
                .flatMap(entity -> {
                    byte[] body = entity.getBody();
                    if (body == null || body.length == 0) {
                        log.warn("Medium image {} returned an empty body", mediumImageId);
                        return Mono.empty();
                    }
                    String contentType = entity.getHeaders().getContentType() != null
                            ? entity.getHeaders().getContentType().toString()
                            : "image/jpeg";
                    return uploadService.store(body, contentType);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to import Medium image {}: {}", mediumImageId, e.getMessage());
                    return Mono.empty();
                });
    }

    // miro.medium.com (the image CDN) 301-redirects every request to a
    // versioned resize path (e.g. /max/1400/<id> -> /v2/resize:fit:1400/<id>)
    // -- confirmed against a real image URL. Reactor Netty's HttpClient does
    // not follow redirects by default, so without this every image download
    // silently received the empty 301 response instead of the actual image.
    //
    // maxInMemorySize is raised well past Spring's 256KB default -- a real
    // Medium article page (fetched to confirm) is ~285KB, just over that
    // default, and bodyToMono(String.class) hitting the limit didn't surface
    // as a clean error: it raced with Netty's buffer release and manifested
    // as an IllegalReferenceCountException that left the response hanging
    // until Cloudflare's edge gave up with its own 502, instead of this
    // service's own error handling ever getting a chance to run.
    // protected (not private) purely for testability: a test subclass can
    // override this to return a WebClient wired to a fake ExchangeFunction
    // instead of a real Netty connector, so fetchArticlePage/
    // downloadAndStoreImage exercise their real parsing/error-handling logic
    // against canned in-process responses -- no real network call, and the
    // SSRF host allowlist in validateFetchUrl is untouched (the fetchUri
    // passed in still has to be a genuine medium.com host; only the actual
    // HTTP dispatch is intercepted, not which hosts are accepted). No
    // behavior change for the real, unsubclassed service.
    protected WebClient redirectFollowingWebClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(25 * 1024 * 1024))
                        .build())
                .build();
    }

    private Mono<Post> saveDraft(String title, String excerpt, String coverImageUrl, String markdown, Long authorId) {
        String base = slugify(title);
        return uniqueSlug(base, base, 1)
                .flatMap(slug -> {
                    Post post = Post.builder()
                            .title(title)
                            .slug(slug)
                            .content(markdown)
                            .excerpt(excerpt)
                            .coverImageUrl(coverImageUrl)
                            .status("DRAFT")
                            .authorId(authorId)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .publishedAt(null)
                            .build();
                    return postRepository.save(post);
                });
    }

    // Local to this service -- PostService.createPost has no slug-dedup logic
    // of its own today (a pre-existing gap), and fixing that app-wide is out
    // of scope here. Imported titles are especially likely to collide (a
    // second import of a similarly-titled draft, or a title matching an
    // existing manually-written post).
    private Mono<String> uniqueSlug(String base, String candidate, int attempt) {
        return postRepository.findBySlug(candidate)
                .flatMap(existing -> uniqueSlug(base, base + "-" + (attempt + 1), attempt + 1))
                .switchIfEmpty(Mono.defer(() -> Mono.just(candidate)));
    }

    private String slugify(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
