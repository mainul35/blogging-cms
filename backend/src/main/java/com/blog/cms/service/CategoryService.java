package com.blog.cms.service;

import com.blog.cms.model.Category;
import com.blog.cms.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Flux<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Mono<Category> createCategory(Category category) {
        category.setSlug(category.getName().toLowerCase().replaceAll("\\s+", "-"));
        return categoryRepository.save(category);
    }

    public Mono<Void> deleteCategory(Long id) {
        return categoryRepository.deleteById(id);
    }
}
