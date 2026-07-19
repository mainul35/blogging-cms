package com.blog.cms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MediumImportRequest {

    @NotBlank
    private String fetchUrl;

    // No automated check that the article actually belongs to the caller --
    // this is the deliberate substitute: the admin must explicitly confirm
    // ownership, and @AssertTrue enforces it can't be silently defaulted/omitted.
    @AssertTrue(message = "You must confirm ownership of this article")
    private boolean ownershipConfirmed;
}
