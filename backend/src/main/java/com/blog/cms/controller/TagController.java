package com.blog.cms.controller;

import com.blog.cms.model.Tag;
import com.blog.cms.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;

    @GetMapping
    public Flux<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Tag> createTag(@RequestBody Tag tag) {
        tag.setSlug(tag.getName().toLowerCase().replaceAll("\\s+", "-"));
        return tagRepository.save(tag);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTag(@PathVariable Long id) {
        return tagRepository.deleteById(id);
    }
}
