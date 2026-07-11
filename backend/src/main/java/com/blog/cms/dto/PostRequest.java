package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PostRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private String excerpt;
    private String coverImageUrl;
    private String status;       // DRAFT | PUBLISHED
    private Long categoryId;
    private List<Long> tagIds;
}
