package com.blog.cms.service;

import com.blog.cms.dto.CommentRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.model.Comment;
import com.blog.cms.model.CommentMention;
import com.blog.cms.model.Post;
import com.blog.cms.model.Reader;
import com.blog.cms.model.User;
import com.blog.cms.repository.CommentMentionRepository;
import com.blog.cms.repository.CommentRepository;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.ReaderRepository;
import com.blog.cms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentMentionRepository commentMentionRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReaderRepository readerRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, commentMentionRepository, postRepository,
                userRepository, readerRepository);
        // toResponse() always looks up mentions for every comment -- stub
        // leniently to empty so tests that don't care about mentions don't
        // need to know this internal detail.
        lenient().when(commentMentionRepository.findAllByCommentId(anyLong())).thenReturn(Flux.empty());
    }

    private Authentication adminAuth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication readerAuth(String handle) {
        return new UsernamePasswordAuthenticationToken(handle, null, List.of(new SimpleGrantedAuthority("ROLE_READER")));
    }

    private Authentication noAuth() {
        return new UsernamePasswordAuthenticationToken("anon", null, List.of());
    }

    private Post post() {
        return Post.builder().id(1L).slug("hello-world").title("Hello World").build();
    }

    private Comment comment(Long id, Long authorId, Long readerId, Long parentId, String body) {
        return Comment.builder()
                .id(id).postId(1L).authorId(authorId).readerId(readerId).parentId(parentId)
                .authorName("Someone").body(body).createdAt(LocalDateTime.now())
                .build();
    }

    // ---- getComments ----

    @Test
    void getComments_unknownSlug_isNotFound() {
        when(postRepository.findBySlug("missing")).thenReturn(Mono.empty());

        StepVerifier.create(commentService.getComments("missing"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void getComments_buildsThreadedTree_repliesNestedUnderParent() {
        Comment root = comment(1L, 10L, null, null, "root comment");
        Comment reply = comment(2L, 10L, null, 1L, "a reply");
        Comment orphanReply = comment(3L, 10L, null, 999L, "reply to a comment that doesn't exist");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(1L)).thenReturn(Flux.just(root, reply, orphanReply));
        when(userRepository.findById(10L)).thenReturn(Mono.just(
                User.builder().id(10L).username("admin").role("ADMIN").build()));

        StepVerifier.create(commentService.getComments("hello-world"))
                .assertNext(r -> {
                    // Only the true root is top-level; the orphan reply
                    // (parentId points at a nonexistent comment) is silently
                    // dropped rather than surfaced or erroring -- matches
                    // CommentService.getComments' index.get(...) null check.
                    assertThat(r.getId()).isEqualTo(1L);
                    assertThat(r.getReplies()).hasSize(1);
                    assertThat(r.getReplies().get(0).getId()).isEqualTo(2L);
                })
                .verifyComplete();
    }

    // ---- addComment ----

    @Test
    void addComment_unknownSlug_isNotFound() {
        when(postRepository.findBySlug("missing")).thenReturn(Mono.empty());

        StepVerifier.create(commentService.addComment("missing", new CommentRequest(), noAuth()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void addComment_asAdmin_savesWithAuthorIdAndNoMentions() {
        CommentRequest request = new CommentRequest();
        request.setBody("Just a note, no mentions here");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(
                User.builder().id(10L).username("admin").email("admin@blog.com").role("ADMIN").build()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(5L);
            return Mono.just(c);
        });
        // toResponse's resolveIdentity() does a *second*, separate
        // userRepository.findById lookup (by authorId) to build the response
        // -- distinct from the findByEmail lookup used to build the Comment
        // itself, and needs its own stub.
        when(userRepository.findById(10L)).thenReturn(Mono.just(
                User.builder().id(10L).username("admin").build()));

        StepVerifier.create(commentService.addComment("hello-world", request, adminAuth("admin@blog.com")))
                .assertNext(r -> assertThat(r.getAuthorType()).isEqualTo("ADMIN"))
                .verifyComplete();

        verify(commentRepository).save(argThat(c -> c.getAuthorId().equals(10L) && c.getReaderId() == null));
        verify(commentMentionRepository, never()).save(any());
    }

    @Test
    void addComment_asReader_savesWithReaderId() {
        CommentRequest request = new CommentRequest();
        request.setBody("Cool post");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(readerRepository.findByHandle("jane")).thenReturn(Mono.just(
                Reader.builder().id(20L).handle("jane").displayName("Jane").email("jane@example.com").build()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(6L);
            return Mono.just(c);
        });
        // Same second lookup as the admin case above, but by reader id.
        when(readerRepository.findById(20L)).thenReturn(Mono.just(Reader.builder().id(20L).handle("jane").build()));

        StepVerifier.create(commentService.addComment("hello-world", request, readerAuth("jane")))
                .assertNext(r -> assertThat(r.getAuthorType()).isEqualTo("READER"))
                .verifyComplete();

        verify(commentRepository).save(argThat(c -> c.getReaderId().equals(20L) && c.getAuthorId() == null));
    }

    @Test
    void addComment_asGuestWithNameAndEmail_succeeds() {
        CommentRequest request = new CommentRequest();
        request.setBody("Nice article");
        request.setAuthorName("Guest Person");
        request.setAuthorEmail("guest@example.com");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(7L);
            return Mono.just(c);
        });

        StepVerifier.create(commentService.addComment("hello-world", request, noAuth()))
                .assertNext(r -> assertThat(r.getAuthorType()).isEqualTo("GUEST"))
                .verifyComplete();
    }

    @Test
    void addComment_asGuestMissingNameOrEmail_isBadRequest() {
        CommentRequest request = new CommentRequest();
        request.setBody("Nice article");
        // authorName/authorEmail left null

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));

        StepVerifier.create(commentService.addComment("hello-world", request, noAuth()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400))
                .verify();

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_mentioningAReader_resolvesAndSavesMention() {
        CommentRequest request = new CommentRequest();
        request.setBody("Thanks for the tip @jane");
        request.setAuthorName("Guest");
        request.setAuthorEmail("guest@example.com");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(8L);
            return Mono.just(c);
        });
        when(readerRepository.findByHandle("jane")).thenReturn(Mono.just(
                Reader.builder().id(20L).handle("jane").build()));
        // switchIfEmpty(userRepository.findByUsername(handle)...) is
        // constructed eagerly regardless of whether the readerRepository
        // lookup above actually succeeds -- same gotcha as PostService's
        // switchIfEmpty tests. Never subscribed to here since the reader
        // lookup does succeed, but still needs a non-null stub.
        when(userRepository.findByUsername("jane")).thenReturn(Mono.empty());
        when(commentMentionRepository.save(any(CommentMention.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(commentService.addComment("hello-world", request, noAuth()))
                .expectNextCount(1)
                .verifyComplete();

        verify(commentMentionRepository).save(argThatMention(m -> m.getMentionedReaderId().equals(20L)));
    }

    @Test
    void addComment_mentioningAdminUsername_fallsBackToUserLookupWhenNoMatchingReader() {
        CommentRequest request = new CommentRequest();
        request.setBody("Question for @admin");
        request.setAuthorName("Guest");
        request.setAuthorEmail("guest@example.com");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(9L);
            return Mono.just(c);
        });
        when(readerRepository.findByHandle("admin")).thenReturn(Mono.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Mono.just(
                User.builder().id(1L).username("admin").build()));
        when(commentMentionRepository.save(any(CommentMention.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(commentService.addComment("hello-world", request, noAuth()))
                .expectNextCount(1)
                .verifyComplete();

        verify(commentMentionRepository).save(argThatMention(m -> m.getMentionedUserId().equals(1L)));
    }

    @Test
    void addComment_mentioningUnresolvableHandle_skipsSilently() {
        CommentRequest request = new CommentRequest();
        request.setBody("Hey @nobody");
        request.setAuthorName("Guest");
        request.setAuthorEmail("guest@example.com");

        when(postRepository.findBySlug("hello-world")).thenReturn(Mono.just(post()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            return Mono.just(c);
        });
        when(readerRepository.findByHandle("nobody")).thenReturn(Mono.empty());
        when(userRepository.findByUsername("nobody")).thenReturn(Mono.empty());

        StepVerifier.create(commentService.addComment("hello-world", request, noAuth()))
                .expectNextCount(1)
                .verifyComplete();

        verify(commentMentionRepository, never()).save(any());
    }

    // ---- getAllComments ----

    @Test
    void getAllComments_populatesPostTitleAndSlug_sortsNewestFirst() {
        Comment older = comment(1L, null, null, null, "older");
        older.setCreatedAt(LocalDateTime.now().minusDays(1));
        Comment newer = comment(2L, null, null, null, "newer");
        newer.setCreatedAt(LocalDateTime.now());

        when(commentRepository.findAll()).thenReturn(Flux.just(older, newer));
        when(postRepository.findById(1L)).thenReturn(Mono.just(post()));

        StepVerifier.create(commentService.getAllComments())
                .assertNext(first -> {
                    assertThat(first.getId()).isEqualTo(2L); // newer first
                    assertThat(first.getPostTitle()).isEqualTo("Hello World");
                    assertThat(first.getPostSlug()).isEqualTo("hello-world");
                })
                .assertNext(second -> assertThat(second.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    // ---- deleteComment ----

    @Test
    void deleteComment_unknownId_isNotFound() {
        when(commentRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(99L, adminAuth("admin@blog.com")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void deleteComment_adminDeletingAnyComment_succeeds() {
        Comment c = comment(1L, 99L, null, null, "someone else's comment"); // not owned by this admin
        when(commentRepository.findById(1L)).thenReturn(Mono.just(c));
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(
                User.builder().id(10L).role("ADMIN").build()));
        when(commentRepository.delete(c)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(1L, adminAuth("admin@blog.com"))).verifyComplete();

        verify(commentRepository).delete(c);
    }

    @Test
    void deleteComment_readerDeletingOwnComment_succeeds() {
        Comment c = comment(1L, null, 20L, null, "my comment");
        when(commentRepository.findById(1L)).thenReturn(Mono.just(c));
        when(readerRepository.findByHandle("jane")).thenReturn(Mono.just(Reader.builder().id(20L).handle("jane").build()));
        when(commentRepository.delete(c)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(1L, readerAuth("jane"))).verifyComplete();

        verify(commentRepository).delete(c);
    }

    @Test
    void deleteComment_readerDeletingSomeoneElsesComment_isForbidden() {
        Comment c = comment(1L, null, 999L, null, "not jane's comment");
        when(commentRepository.findById(1L)).thenReturn(Mono.just(c));
        when(readerRepository.findByHandle("jane")).thenReturn(Mono.just(Reader.builder().id(20L).handle("jane").build()));

        StepVerifier.create(commentService.deleteComment(1L, readerAuth("jane")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();

        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void deleteComment_unauthenticatedOrGuest_isForbidden() {
        Comment c = comment(1L, null, null, null, "a guest comment");
        when(commentRepository.findById(1L)).thenReturn(Mono.just(c));

        StepVerifier.create(commentService.deleteComment(1L, noAuth()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();

        verify(commentRepository, never()).delete(any(Comment.class));
    }

    private static Comment argThat(java.util.function.Predicate<Comment> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    private static CommentMention argThatMention(java.util.function.Predicate<CommentMention> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
