package com.blog.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Stored directly as the Redis value for a job (see RedisConfig's
// GenericJackson2JsonRedisSerializer with default typing) -- read back with
// .cast(MediumImportJobStatusResponse.class), same pattern CacheService uses
// for PostResponse. Doubles as the GET .../status response body, so fields
// outside the current state (e.g. postId while PENDING) are simply null
// rather than needing a separate wire DTO.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediumImportJobStatusResponse {
    private String jobId;
    private MediumImportJobState state;

    // Populated only once state == DONE
    private Long postId;
    private String slug;
    private String title;
    private Integer imagesImported;
    private Integer imagesFailed;
    private List<String> warnings;

    // Populated only once state == FAILED
    private String errorMessage;
}
