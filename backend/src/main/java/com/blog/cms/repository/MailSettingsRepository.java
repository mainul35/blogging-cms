package com.blog.cms.repository;

import com.blog.cms.model.MailSettings;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface MailSettingsRepository extends ReactiveCrudRepository<MailSettings, Long> {
}
