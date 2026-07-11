package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentRequest {

    @NotBlank(message = "Comment body must not be empty")
    private String body;

    private String authorName;   // required when posting as a guest
    private String authorEmail;  // required when posting as a guest

    private Long parentId;       // set to reply to an existing comment
}
