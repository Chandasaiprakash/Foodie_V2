package com.foodie.delivery_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import java.util.concurrent.Executors;

/**
 * Virtual thread configuration for delivery-service.
 * See order-service VirtualThreadConfig for full design notes.
 */
@EnableAsync
@Configuration
public class VirtualThreadConfig {

    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public TaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("delivery-service-scheduler-");
        return scheduler;
    }
}
