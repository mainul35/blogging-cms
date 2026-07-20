package com.blog.cms.service;

import com.blog.cms.model.Category;
import com.blog.cms.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void getAllCategories_delegatesToRepository() {
        when(categoryRepository.findAll()).thenReturn(Flux.just(Category.builder().id(1L).name("Tech").build()));

        StepVerifier.create(categoryService.getAllCategories())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void createCategory_slugifiesName() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(categoryService.createCategory(Category.builder().name("Web Development").build()))
                .assertNext(c -> assertThat(c.getSlug()).isEqualTo("web-development"))
                .verifyComplete();
    }

    @Test
    void deleteCategory_delegatesToRepository() {
        when(categoryRepository.deleteById(5L)).thenReturn(Mono.empty());

        StepVerifier.create(categoryService.deleteCategory(5L)).verifyComplete();

        verify(categoryRepository).deleteById(5L);
    }
}
