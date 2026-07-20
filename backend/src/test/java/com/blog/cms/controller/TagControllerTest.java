package com.blog.cms.controller;

import com.blog.cms.model.Tag;
import com.blog.cms.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock private TagRepository tagRepository;

    private TagController controller;

    @BeforeEach
    void setUp() {
        controller = new TagController(tagRepository);
    }

    @Test
    void getAllTags_delegates() {
        when(tagRepository.findAll()).thenReturn(Flux.just(Tag.builder().id(1L).build()));

        StepVerifier.create(controller.getAllTags()).expectNextCount(1).verifyComplete();
    }

    @Test
    void createTag_slugifiesNameBeforeSaving() {
        Tag tag = Tag.builder().name("Web Dev").build();
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(controller.createTag(tag))
                .assertNext(saved -> assertThat(saved.getSlug()).isEqualTo("web-dev"))
                .verifyComplete();
    }

    @Test
    void deleteTag_delegatesId() {
        when(tagRepository.deleteById(9L)).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteTag(9L)).verifyComplete();

        verify(tagRepository).deleteById(9L);
    }
}
