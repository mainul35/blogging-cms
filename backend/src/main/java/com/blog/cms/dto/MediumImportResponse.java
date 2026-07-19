package com.blog.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediumImportResponse {
    private Long postId;
    private String slug;
    private String title;
    private int imagesImported;
    private int imagesFailed;
    // Things like "Skipped unrecognized paragraph type: X" -- surfaced to the
    // admin since Medium's JSON shape is a best-effort assumption, not
    // verified against a live response; this is the signal for whether a real
    // import needs a follow-up fix to the converter.
    private List<String> warnings;
}
