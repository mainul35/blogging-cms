package com.blog.cms.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    public Mono<MediumImportResponse> importArticle(MediumImportRequest request, String authorEmail) {
        URI fetchUri = validateFetchUrl(request.getFetchUrl());

        return isWithinRateLimit(authorEmail)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS, "Too many Medium imports -- try again later"));
                    }
                    return userRepository.findByEmail(authorEmail)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED, "Author not found")))
                            .flatMap(author -> fetchArticle(fetchUri)
                                    .flatMap(root -> buildPost(root, author.getId())));
                });
    }

    // Runs before any network call. This endpoint makes the server perform an
    // admin-supplied outbound GET -- without restricting the host, it's an
    // SSRF hole (could be pointed at internal services or a cloud metadata
    // endpoint). Deliberately checks the exact host or a *.medium.com suffix,
    // not a substring/contains check, to reject suffix-spoofing hosts like
    // medium.com.evil.com.
    private URI validateFetchUrl(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
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
    // Medium serves publicly. Same inline-per-call WebClient pattern as
    // ResendMailSender/SendGridMailSender (no shared bean exists in this
    // codebase for outbound HTTP).
    private Mono<JsonNode> fetchArticle(URI fetchUri) {
        WebClient webClient = WebClient.builder().build();
        return webClient.get()
                .uri(fetchUri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .onErrorMap(e -> !(e instanceof ResponseStatusException), e ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Could not fetch article from Medium: " + e.getMessage()))
                .flatMap(raw -> {
                    try {
                        String stripped = MediumArticleConverter.stripXssiPrefix(raw);
                        return Mono.just(objectMapper.readTree(stripped));
                    } catch (Exception e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Unexpected response shape from Medium -- check you copied the "
                                        + "article-data request, not a different one"));
                    }
                });
    }

    private Mono<MediumImportResponse> buildPost(JsonNode root, Long authorId) {
        JsonNode value;
        try {
            value = MediumArticleConverter.extractPayloadValue(root);
        } catch (IllegalArgumentException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
        }

        String title = value.path("title").asText("Untitled");
        String rawExcerpt = value.path("content").path("subtitle").asText(null);
        String excerpt = (rawExcerpt == null || rawExcerpt.isBlank()) ? null : rawExcerpt;
        JsonNode paragraphsNode = value.path("content").path("bodyModel").path("paragraphs");
        String previewImageId = value.path("virtuals").path("previewImage").path("id").asText(null);

        List<String> warnings = new ArrayList<>();
        List<ParagraphBlock> blocks = MediumArticleConverter.parseParagraphs(paragraphsNode, warnings);

        AtomicInteger imagesImported = new AtomicInteger();
        AtomicInteger imagesFailed = new AtomicInteger();

        Mono<String> coverImageMono = (previewImageId == null || previewImageId.isBlank())
                ? Mono.<String>empty()
                : downloadAndStoreImage(previewImageId).doOnNext(url -> imagesImported.incrementAndGet());

        return coverImageMono.defaultIfEmpty("")
                .flatMap(coverUrl -> resolveBody(blocks, imagesImported, imagesFailed)
                        .flatMap(markdown -> saveDraft(title, excerpt,
                                coverUrl.isBlank() ? null : coverUrl, markdown, authorId))
                        .map(post -> MediumImportResponse.builder()
                                .postId(post.getId())
                                .slug(post.getSlug())
                                .title(post.getTitle())
                                .imagesImported(imagesImported.get())
                                .imagesFailed(imagesFailed.get())
                                .warnings(warnings)
                                .build()));
    }

    // Sequential (concatMap), not parallel -- avoids hammering miro.medium.com
    // with a burst of concurrent requests and keeps output order deterministic.
    private Mono<String> resolveBody(List<ParagraphBlock> blocks, AtomicInteger imagesImported,
                                      AtomicInteger imagesFailed) {
        List<String> blockMarkdown = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();

        return Flux.fromIterable(blocks)
                .concatMap(block -> resolveBlockMarkdown(block, imagesImported, imagesFailed)
                        .doOnNext(md -> {
                            blockMarkdown.add(md);
                            blockTypes.add(block.type());
                        }))
                .then(Mono.fromCallable(() -> MediumArticleConverter.assembleMarkdown(blockMarkdown, blockTypes)));
    }

    private Mono<String> resolveBlockMarkdown(ParagraphBlock block, AtomicInteger imagesImported,
                                               AtomicInteger imagesFailed) {
        if ("IMG".equals(block.type())) {
            return downloadAndStoreImage(block.imageId())
                    .doOnNext(url -> imagesImported.incrementAndGet())
                    .map(url -> "![](" + url + ")")
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
        return WebClient.builder().build().get()
                .uri(cdnUrl)
                .retrieve()
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(15))
                .flatMap(entity -> {
                    byte[] body = entity.getBody();
                    if (body == null || body.length == 0) return Mono.empty();
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
