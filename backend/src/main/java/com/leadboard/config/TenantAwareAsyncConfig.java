package com.leadboard.config;

import com.leadboard.tenant.TenantContext;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * BUG-108: Propagates TenantContext (ThreadLocal) to @Async threads.
 * Without this, @Async methods lose tenant context and operate on the public schema.
 */
@Configuration
@EnableAsync
public class TenantAwareAsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tenant-async-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * TaskDecorator that captures TenantContext from the calling thread
     * and restores it in the async worker thread.
     */
    static class TenantContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture context from the calling thread
            Long tenantId = TenantContext.getCurrentTenantId();
            String schema = TenantContext.getCurrentSchema();

            return () -> {
                try {
                    // Restore context in the async thread
                    if (tenantId != null) {
                        TenantContext.setTenant(tenantId, schema);
                    }
                    runnable.run();
                } finally {
                    TenantContext.clear();
                }
            };
        }
    }
}
