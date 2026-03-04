package com.leadboard.config;

import com.leadboard.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantContextTaskDecorator")
class TenantContextTaskDecoratorTest {

    private final TaskDecorator decorator = new TenantAwareAsyncConfig.TenantContextTaskDecorator();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should propagate TenantContext to separate async thread")
    void shouldPropagateTenantContextToSeparateThread() throws Exception {
        TenantContext.setTenant(1L, "tenant_acme");

        AtomicLong capturedTenantId = new AtomicLong();
        AtomicReference<String> capturedSchema = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            capturedSchema.set(TenantContext.getCurrentSchema());
        });

        // Run in a separate thread (simulates normal async execution)
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(decorated).get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(capturedTenantId.get()).isEqualTo(1L);
        assertThat(capturedSchema.get()).isEqualTo("tenant_acme");
    }

    @Test
    @DisplayName("Should clear TenantContext after task completes in separate thread")
    void shouldClearContextInSeparateThread() throws Exception {
        TenantContext.setTenant(1L, "tenant_acme");

        AtomicReference<String> schemaAfterTask = new AtomicReference<>();
        CountDownLatch taskDone = new CountDownLatch(1);

        Runnable decorated = decorator.decorate(() -> {
            // Task runs with context
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            decorated.run();
            // After decorator's finally block, context should be cleared in this thread
            schemaAfterTask.set(TenantContext.getCurrentSchema());
            taskDone.countDown();
        }).get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(taskDone.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(schemaAfterTask.get()).isEqualTo("public");
    }

    @Test
    @DisplayName("Should NOT clear TenantContext when running in caller's thread (CallerRunsPolicy)")
    void shouldNotClearContextInCallerThread() {
        TenantContext.setTenant(1L, "tenant_acme");

        Runnable decorated = decorator.decorate(() -> {
            // Task runs synchronously in the calling thread (CallerRunsPolicy scenario)
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(1L);
            assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");
        });

        // Run directly in the current thread — simulates CallerRunsPolicy
        decorated.run();

        // TenantContext must NOT be cleared
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo(1L);
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");
    }

    @Test
    @DisplayName("Should handle null TenantContext gracefully")
    void shouldHandleNullTenantContext() throws Exception {
        // No TenantContext set (null tenantId)
        assertThat(TenantContext.getCurrentTenantId()).isNull();

        AtomicReference<String> capturedSchema = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            capturedSchema.set(TenantContext.getCurrentSchema());
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(decorated).get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // With null tenantId, schema should remain default "public"
        assertThat(capturedSchema.get()).isEqualTo("public");
    }

    @Test
    @DisplayName("Should handle task exception without leaking TenantContext in separate thread")
    void shouldClearContextOnTaskException() throws Exception {
        TenantContext.setTenant(1L, "tenant_acme");

        AtomicReference<String> schemaAfterException = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("Task failed");
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try {
                decorated.run();
            } catch (RuntimeException ignored) {
            }
            schemaAfterException.set(TenantContext.getCurrentSchema());
        }).get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // Context should be cleared even after exception
        assertThat(schemaAfterException.get()).isEqualTo("public");
    }

    @Test
    @DisplayName("Should preserve caller's TenantContext even when task throws exception (CallerRunsPolicy)")
    void shouldPreserveCallerContextOnExceptionInCallerThread() {
        TenantContext.setTenant(1L, "tenant_acme");

        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("Task failed");
        });

        try {
            decorated.run();
        } catch (RuntimeException ignored) {
        }

        // CallerRunsPolicy: TenantContext must NOT be cleared even after exception
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo(1L);
        assertThat(TenantContext.getCurrentSchema()).isEqualTo("tenant_acme");
    }
}
