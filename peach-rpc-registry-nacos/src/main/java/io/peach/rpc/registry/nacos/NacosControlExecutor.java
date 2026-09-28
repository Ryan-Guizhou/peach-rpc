package io.peach.rpc.registry.nacos;

import io.peach.rpc.api.ServiceKey;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Nacos 阻塞控制面调用的有界线程隔离器。 */
final class NacosControlExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;

    NacosControlExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(
                    task,
                    "peach-rpc-nacos-control-"
                            + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                2,
                4,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
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
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(action.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(new IllegalStateException(
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

    @Override
    public void close() {
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
