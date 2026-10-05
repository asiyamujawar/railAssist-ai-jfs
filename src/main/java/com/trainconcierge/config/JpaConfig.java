package com.trainconcierge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables JPA auditing so that @CreatedDate / @LastModifiedDate
 * annotations on BaseEntity are populated automatically.
 *
 * Also enables Spring's task scheduling (@Scheduled) for the
 * SeatAlertMonitorService polling cycle.
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.trainconcierge")
@EnableScheduling
public class JpaConfig {
}
