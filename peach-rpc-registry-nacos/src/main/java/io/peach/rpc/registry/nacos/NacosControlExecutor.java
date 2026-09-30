package io.peach.rpc.registry.nacos;

import io.peach.rpc.api.ServiceKey;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Nacos 阻塞控制面调用的有界线程隔离器。 */
final class NacosControlExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;

    NacosControlExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> daemonThread(
                task,
                "peach-rpc-nacos-control-"
                        + sequence.incrementAndGet());
        executor = new ThreadPoolExecutor(
                2,
                4,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                factory,
                new ThreadPoolExecutor.AbortPolicy());

        AtomicInteger schedulerSequence =
                new AtomicInteger();
        scheduler = new ScheduledThreadPoolExecutor(
                1,
                task -> daemonThread(
                        task,
                        "peach-rpc-nacos-scheduler-"
                                + schedulerSequence
                                        .incrementAndGet()));
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(
                false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(
                false);
    }

    <T> CompletableFuture<T> submit(
            String operation,
            ServiceKey serviceKey,
            CheckedSupplier<T> action) {
        return submit(
                operation,
                serviceKey.canonicalName(),
                action);
    }

    CompletableFuture<Void> submit(
            String operation,
            ServiceKey serviceKey,
            CheckedRunnable action) {
        return submit(
                operation,
                serviceKey,
                () -> {
                    action.run();
                    return null;
                });
    }

    <T> CompletableFuture<T> submit(
            String operation,
            String subject,
            CheckedSupplier<T> action) {
        CompletableFuture<T> result =
                new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(action.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(
                    new IllegalStateException(
                            "Nacos control executor queue is full: operation="
                                    + operation
                                    + ", subject="
                                    + subject,
                            error));
        }
        return result;
    }

    void execute(
            String operation,
            ServiceKey serviceKey,
            Runnable action) {
        submit(
                operation,
                serviceKey,
                () -> {
                    action.run();
                    return null;
                });
    }

    ScheduledFuture<?> scheduleWithFixedDelay(
            String operation,
            String subject,
            Duration initialDelay,
            Duration delay,
            Runnable action) {
        Objects.requireNonNull(
                initialDelay,
                "initialDelay");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(action, "action");
        if (initialDelay.isNegative()
                || delay.isZero()
                || delay.isNegative()) {
            throw new IllegalArgumentException(
                    "Nacos control schedule requires "
                            + "non-negative initialDelay "
                            + "and positive delay");
        }
        return scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        submit(
                                operation,
                                subject,
                                () -> {
                                    action.run();
                                    return null;
                                }).join();
                    } catch (RuntimeException ignored) {
                        // Keep the periodic reconcile alive. The next fixed
                        // delay starts only after this attempt has completed.
                    }
                },
                initialDelay.toMillis(),
                delay.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static Thread daemonThread(
            Runnable task,
            String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }
}
