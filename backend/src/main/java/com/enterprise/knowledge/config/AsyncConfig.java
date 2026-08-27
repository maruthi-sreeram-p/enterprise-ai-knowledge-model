package com.enterprise.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * The pool that runs document ingestion when {@code app.ingestion.mode} is
     * IN_PROCESS.
     *
     * The pool is small on purpose. Each task is dominated by calls to the Gemini
     * embedding API, and running many at once buys nothing but rate-limit errors.
     *
     * The queue is bounded and the rejection policy is caller-runs. If ingestion
     * ever falls far enough behind to fill the queue, the upload request itself
     * performs the work: that request becomes slow, which throttles uploads at
     * the source. The alternative, an unbounded queue, would accept work
     * indefinitely and eventually exhaust the heap.
     */
    @Bean
    public TaskExecutor documentIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Give in-flight embedding a chance to finish on shutdown rather than
        // leaving a document stuck in PROCESSING.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
