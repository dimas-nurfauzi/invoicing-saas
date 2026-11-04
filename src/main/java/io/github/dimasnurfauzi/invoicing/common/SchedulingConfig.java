package io.github.dimasnurfauzi.invoicing.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs (e.g. the overdue job). A cron of "-" disables a job. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
