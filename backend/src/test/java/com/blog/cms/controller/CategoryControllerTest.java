package com.blog.cms.controller;

import com.blog.cms.model.Category;
import com.blog.cms.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock private CategoryService categoryService;

    private CategoryController controller;

    @BeforeEach
    void setUp() {
        controller = new CategoryController(categoryService);
    }

    @Test
    void getAllCategories_delegates() {
        when(categoryService.getAllCategories()).thenReturn(Flux.just(Category.builder().id(1L).build()));

        StepVerifier.create(controller.getAllCategories()).expectNextCount(1).verifyComplete();
    }

    @Test
    void createCategory_delegates() {
        Category category = Category.builder().name("Tech").build();
        Category saved = Category.builder().id(1L).name("Tech").slug("tech").build();
        when(categoryService.createCategory(category)).thenReturn(Mono.just(saved));

        StepVerifier.create(controller.createCategory(category)).expectNext(saved).verifyComplete();
    }

    @Test
    void deleteCategory_delegatesId() {
        when(categoryService.deleteCategory(5L)).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteCategory(5L)).verifyComplete();

        verify(categoryService).deleteCategory(5L);
    }
}
