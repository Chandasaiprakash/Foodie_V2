package com.foodie.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Virtual thread configuration for auth-service.
 *
 * <p>Spring Boot 3.2+ automatically routes Tomcat request threads to virtual threads
 * when {@code spring.threads.virtual.enabled=true} is set. This class handles the
 * remaining execution contexts that are <em>not</em> covered by that property:
 *
 * <ul>
 *   <li><b>Async tasks</b> ({@code @Async} methods, {@code @EventListener} async events)
 *       — wired via the {@code applicationTaskExecutor} bean.</li>
 *   <li><b>Kafka listener threads</b> — each listener container's thread factory is
 *       replaced via {@code ContainerCustomizer} in the service-specific Kafka consumer
 *       config, pointing to {@code Thread.ofVirtual().factory()}.</li>
 * </ul>
 *
 * <p>No thread pool sizes need tuning. Virtual threads are cheap enough that the JVM
 * scheduler handles concurrency automatically — the previous fixed pool limits
 * (e.g. Tomcat {@code max-threads=200}) become irrelevant.
 *
 * <p><b>Pinning awareness:</b> Virtual threads pin their carrier thread when a
 * {@code synchronized} block or native method is executing. The known pinning
 * risk in this codebase is Hibernate's connection acquisition path. Spring Boot
 * 3.2+ with Hibernate 6.2+ mitigates this via the connection pool's virtual-thread
 * friendly locking; no action is needed here but the JVM flag
 * {@code -Djdk.tracePinnedThreads=short} can be added to the Dockerfile for
 * observability during load testing.
 */
@EnableAsync
@Configuration
public class VirtualThreadConfig {

    /**
     * Replaces the default {@code ThreadPoolTaskExecutor} used by {@code @Async}
     * with a virtual-thread-per-task executor.
     *
     * <p>Spring Boot auto-configuration picks this up as the primary
     * {@code ApplicationTaskExecutor} when it is present in the context.
     */
    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
