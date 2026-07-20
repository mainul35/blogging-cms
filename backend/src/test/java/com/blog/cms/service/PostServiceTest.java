package com.blog.cms.service;

import com.blog.cms.dto.PostRequest;
import com.blog.cms.dto.PostResponse;
import com.blog.cms.model.Category;
import com.blog.cms.model.Post;
import com.blog.cms.model.User;
import com.blog.cms.repository.CategoryRepository;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CacheService cacheService;
    @Mock private UploadService uploadService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, categoryRepository, cacheService, uploadService);
        // Most tests exercise a post with no category -- stub leniently so
        // categoryId-less tests don't need to know this internal detail.
        lenient().when(userRepository.findById(anyLong())).thenReturn(Mono.just(author()));
    }

    private User author() {
        return User.builder().id(1L).username("Admin").email("admin@blog.com").role("ADMIN").build();
    }

    private Post samplePost() {
        return Post.builder()
                .id(10L)
                .title("Hello World")
                .slug("hello-world")
                .content("Some content with ![](/uploads/a.png)")
                .coverImageUrl("/uploads/cover.png")
                .status("DRAFT")
                .authorId(1L)
                .categoryId(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PostRequest request(String status) {
        PostRequest r = new PostRequest();
        r.setTitle("Hello World");
        r.setContent("New content");
        r.setStatus(status);
        return r;
    }

    // ---- getAllPosts ----

    @Test
    void getAllPosts_statusAll_usesFindAll() {
        when(postRepository.findAll()).thenReturn(Flux.just(samplePost()));

        StepVerifier.create(postService.getAllPosts("all"))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository).findAll();
        verify(postRepository, never()).findAllByStatus(any());
    }

    @Test
    void getAllPosts_specificStatus_upperCasesAndFiltersByStatus() {
        when(postRepository.findAllByStatus("PUBLISHED")).thenReturn(Flux.just(samplePost()));

        StepVerifier.create(postService.getAllPosts("published"))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository).findAllByStatus("PUBLISHED");
    }

    // ---- getPostById ----

    @Test
    void getPostById_found_mapsToResponse() {
        when(postRepository.findById(10L)).thenReturn(Mono.just(samplePost()));

        StepVerifier.create(postService.getPostById(10L))
                .assertNext(r -> assertThat(r.getTitle()).isEqualTo("Hello World"))
                .verifyComplete();
    }

    @Test
    void getPostById_missing_isNotFound() {
        when(postRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(postService.getPostById(999L))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    // ---- getPostBySlug (cache-through) ----

    @Test
    void getPostBySlug_cacheHit_neverSubscribesToRepositoryFallback() {
        PostResponse cached = PostResponse.builder().id(10L).slug("hello-world").title("Hello World").build();
        when(cacheService.getPost("hello-world")).thenReturn(Mono.just(cached));
        // switchIfEmpty(postRepository.findBySlug(slug)...) is passed as an
        // ordinary Java method argument, so findBySlug(slug) is CALLED
        // (constructing a Mono) unconditionally while building the chain,
        // even though Reactor only actually SUBSCRIBES to that Mono if the
        // cache truly comes back empty. An unstubbed mock call here throws
        // NPE at chain-construction time regardless of cache hit/miss --
        // confirmed by this test itself before this stub was added.
        when(postRepository.findBySlug(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(postService.getPostBySlug("hello-world"))
                .assertNext(r -> assertThat(r.getTitle()).isEqualTo("Hello World"))
                .verifyComplete();

        // The real assertion: the fallback Mono is constructed (unavoidably,
        // see above) but never actually subscribed to -- so the underlying
        // repository method invocation happens, yet its result is discarded
        // and no further calls (toResponse, cachePost) occur off of it.
        verify(cacheService, never()).cachePost(any(), any());
    }

    @Test
    void getPostBySlug_cacheMiss_readsRepositoryThenCachesResult() {
        when(cacheService.getPost("hello-world")).thenReturn(Mono.empty());
        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(samplePost()));
        when(cacheService.cachePost(eq("hello-world"), any(PostResponse.class))).thenReturn(Mono.just(true));

        StepVerifier.create(postService.getPostBySlug("hello-world"))
                .assertNext(r -> assertThat(r.getSlug()).isEqualTo("hello-world"))
                .verifyComplete();

        verify(cacheService).cachePost(eq("hello-world"), any(PostResponse.class));
    }

    @Test
    void getPostBySlug_cacheMissAndNoSuchPost_isNotFound() {
        when(cacheService.getPost("missing")).thenReturn(Mono.empty());
        when(postRepository.findBySlug("missing")).thenReturn(Mono.empty());

        StepVerifier.create(postService.getPostBySlug("missing"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    // ---- createPost ----

    @Test
    void createPost_draftStatus_savesWithNoPublishedAt() {
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(author()));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(11L);
            return Mono.just(p);
        });

        StepVerifier.create(postService.createPost(request("DRAFT"), "admin@blog.com"))
                .assertNext(r -> assertThat(r.getStatus()).isEqualTo("DRAFT"))
                .verifyComplete();

        verify(postRepository).save(argThat(p -> p.getStatus().equals("DRAFT") && p.getPublishedAt() == null));
    }

    @Test
    void createPost_publishedStatus_setsPublishedAt() {
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(author()));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(12L);
            return Mono.just(p);
        });

        StepVerifier.create(postService.createPost(request("PUBLISHED"), "admin@blog.com"))
                .assertNext(r -> assertThat(r.getStatus()).isEqualTo("PUBLISHED"))
                .verifyComplete();

        verify(postRepository).save(argThat(p -> p.getStatus().equals("PUBLISHED") && p.getPublishedAt() != null));
    }

    @Test
    void createPost_slugifiesTitle() {
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(author()));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        PostRequest r = request("DRAFT");
        // No leading/trailing whitespace on purpose -- slugify() doesn't trim,
        // so a title with surrounding spaces would produce leading/trailing
        // hyphens instead (a separate, real behavior worth its own test if
        // ever intentionally exercised -- not the point of this one).
        r.setTitle("Hello, World! It's a Test");

        StepVerifier.create(postService.createPost(r, "admin@blog.com"))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository).save(argThat(p -> p.getSlug().equals("hello-world-its-a-test")));
    }

    @Test
    void createPost_unknownAuthor_isUnauthorized() {
        when(userRepository.findByEmail("ghost@blog.com")).thenReturn(Mono.empty());

        StepVerifier.create(postService.createPost(request("DRAFT"), "ghost@blog.com"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();

        verify(postRepository, never()).save(any());
    }

    // ---- updatePost ----

    @Test
    void updatePost_missingPost_isNotFound() {
        when(postRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(999L, request("DRAFT")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void updatePost_transitioningDraftToPublished_setsPublishedAt() {
        Post existing = samplePost(); // status DRAFT, publishedAt null
        when(postRepository.findById(10L)).thenReturn(Mono.just(existing));
        when(uploadService.extractLocalFilenames(any(), any())).thenReturn(Set.of());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cacheService.evictPost("hello-world")).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(10L, request("PUBLISHED")))
                .assertNext(r -> assertThat(r.getStatus()).isEqualTo("PUBLISHED"))
                .verifyComplete();

        verify(postRepository).save(argThat(p -> p.getPublishedAt() != null));
        verify(cacheService).evictPost("hello-world");
    }

    @Test
    void updatePost_alreadyPublished_doesNotResetPublishedAt() {
        LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(3);
        Post existing = samplePost();
        existing.setStatus("PUBLISHED");
        existing.setPublishedAt(originalPublishedAt);

        when(postRepository.findById(10L)).thenReturn(Mono.just(existing));
        when(uploadService.extractLocalFilenames(any(), any())).thenReturn(Set.of());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cacheService.evictPost("hello-world")).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(10L, request("PUBLISHED")))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository).save(argThat(p -> p.getPublishedAt().equals(originalPublishedAt)));
    }

    @Test
    void updatePost_removedImageNoLongerReferencedByOtherPosts_isDeleted() {
        Post existing = samplePost();
        existing.setContent("![](/uploads/removed.png)");
        existing.setCoverImageUrl(null);

        when(postRepository.findById(10L)).thenReturn(Mono.just(existing));
        // Before: {removed.png}; After (new request content has none): {}
        when(uploadService.extractLocalFilenames("![](/uploads/removed.png)", null)).thenReturn(Set.of("removed.png"));
        when(uploadService.extractLocalFilenames("New content", null)).thenReturn(Set.of());
        when(postRepository.countOtherPostsReferencingFile(10L, "removed.png")).thenReturn(Mono.just(0L));
        when(uploadService.deleteFiles(anySet())).thenReturn(Mono.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cacheService.evictPost("hello-world")).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(10L, request("DRAFT")))
                .expectNextCount(1)
                .verifyComplete();

        verify(uploadService).deleteFiles(Set.of("removed.png"));
    }

    @Test
    void updatePost_removedImageStillReferencedByAnotherPost_isKept() {
        Post existing = samplePost();
        existing.setContent("![](/uploads/shared.png)");
        existing.setCoverImageUrl(null);

        when(postRepository.findById(10L)).thenReturn(Mono.just(existing));
        when(uploadService.extractLocalFilenames("![](/uploads/shared.png)", null)).thenReturn(Set.of("shared.png"));
        when(uploadService.extractLocalFilenames("New content", null)).thenReturn(Set.of());
        when(postRepository.countOtherPostsReferencingFile(10L, "shared.png")).thenReturn(Mono.just(1L));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cacheService.evictPost("hello-world")).thenReturn(Mono.empty());
        // cleanupOrphanedImages still calls uploadService.deleteFiles with
        // whatever survives the countOtherPostsReferencingFile filter -- here
        // that's an empty set (the only candidate is still referenced), but
        // the call happens regardless; only the top-level "no candidates at
        // all" case short-circuits before ever calling deleteFiles.
        when(uploadService.deleteFiles(anySet())).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(10L, request("DRAFT")))
                .expectNextCount(1)
                .verifyComplete();

        // deleteFiles is still called (see comment above) but with an empty
        // set, since the only candidate survived the still-referenced filter.
        verify(uploadService).deleteFiles(Set.of());
    }

    // ---- deletePost ----

    @Test
    void deletePost_missingPost_isNotFound() {
        when(postRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(postService.deletePost(999L))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void deletePost_existingPost_deletesEvictsCacheAndCleansOrphanedImages() {
        Post existing = samplePost();
        when(postRepository.findById(10L)).thenReturn(Mono.just(existing));
        when(postRepository.delete(existing)).thenReturn(Mono.empty());
        when(cacheService.evictPost("hello-world")).thenReturn(Mono.empty());
        when(uploadService.extractLocalFilenames(existing.getContent(), existing.getCoverImageUrl()))
                .thenReturn(Set.of("a.png", "cover.png"));
        when(postRepository.countOtherPostsReferencingFile(eq(10L), any())).thenReturn(Mono.just(0L));
        when(uploadService.deleteFiles(anySet())).thenReturn(Mono.empty());

        StepVerifier.create(postService.deletePost(10L)).verifyComplete();

        verify(postRepository).delete(existing);
        verify(cacheService).evictPost("hello-world");
        verify(uploadService).deleteFiles(Set.of("a.png", "cover.png"));
    }

    // ---- toResponse category lookup ----

    @Test
    void getPostById_withCategory_resolvesCategoryName() {
        Post post = samplePost();
        post.setCategoryId(5L);
        when(postRepository.findById(10L)).thenReturn(Mono.just(post));
        when(categoryRepository.findById(5L)).thenReturn(Mono.just(Category.builder().id(5L).name("Tech").build()));

        StepVerifier.create(postService.getPostById(10L))
                .assertNext(r -> assertThat(r.getCategoryName()).isEqualTo("Tech"))
                .verifyComplete();
    }

    private static Post argThat(java.util.function.Predicate<Post> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
