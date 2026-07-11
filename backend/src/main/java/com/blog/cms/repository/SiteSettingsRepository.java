package com.blog.cms.repository;

import com.blog.cms.model.SiteSettings;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SiteSettingsRepository extends ReactiveCrudRepository<SiteSettings, Long> {
}
