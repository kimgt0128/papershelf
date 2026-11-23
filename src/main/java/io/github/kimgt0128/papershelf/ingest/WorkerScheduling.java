package io.github.kimgt0128.papershelf.ingest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "papershelf.worker-enabled", havingValue = "true", matchIfMissing = true)
class WorkerScheduling {}
