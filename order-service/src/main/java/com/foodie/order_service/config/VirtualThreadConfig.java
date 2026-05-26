package com.foodie.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import java.util.concurrent.Executors;

/**
 * Virtual thread configuration for order-service.
 *
 * <p>Covers three execution contexts:
 * <ol>
 *   <li><b>Tomcat request threads</b> — handled by {@code spring.threads.virtual.enabled=true}
 *       in application.properties (Spring Boot auto-configuration).</li>
 *   <li><b>{@code @Async} methods</b> — wired via {@code applicationTaskExecutor} bean below.</li>
 *   <li><b>{@code @Scheduled} tasks</b> — wired via {@code taskScheduler} bean below.
 *       This covers the {@code OutboxPoller}, {@code OutboxCleanupScheduler}, and
 *       {@code IdempotencyCleanupScheduler}, all of which perform I/O (DB reads/writes)
 *       and benefit from running on virtual threads.</li>
 * </ol>
 *
 * <p>Kafka listener dispatch is handled separately in {@code KafkaConsumerConfig}
 * via {@code setListenerTaskExecutor}.
 *
 * <p><b>Pinning note:</b> Virtual threads pin their carrier when inside a
 * {@code synchronized} block. The JVM flag {@code -Djdk.tracePinnedThreads=short}
 * is set in the Dockerfile to surface any pinning events during load testing.
 * Hibernate 6.2+ (bundled with Spring Boot 3.2+) uses ReentrantLock instead of
 * synchronized for connection acquisition, so the main pinning risk is mitigated.
 */
@EnableAsync
@Configuration
public class VirtualThreadConfig {

    /**
     * Replaces the default {@code ThreadPoolTaskExecutor} used by {@code @Async}
     * with a virtual-thread-per-task executor.
     */
    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Replaces the default single-threaded {@code ThreadPoolTaskScheduler} used
     * by {@code @Scheduled} with Spring's {@code SimpleAsyncTaskScheduler} backed
     * by virtual threads.
     *
     * <p>{@code SimpleAsyncTaskScheduler} is Spring's native virtual-thread-aware
     * scheduler (introduced in Spring Framework 6.1). It spawns a new virtual
     * thread for every scheduled execution rather than reusing a pool — correct
     * for tasks that do I/O (OutboxPoller, cleanup schedulers).
     */
    @Bean
    public TaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("order-service-scheduler-");
        return scheduler;
    }
}
