package io.peach.rpc.spring.lifecycle;

import io.peach.rpc.core.PeachRpcServer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/**
 * 将 Peach RPC Provider 生命周期接入 Spring 容器生命周期。
 */
public final class PeachRpcServerLifecycle implements SmartLifecycle {

    private final PeachRpcServer server;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 创建 Provider 生命周期适配器。
     *
     * @param server Provider 运行时
     */
    public PeachRpcServerLifecycle(PeachRpcServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                server.start().toCompletableFuture().join();
            } catch (RuntimeException error) {
                running.set(false);
                throw error;
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            server.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
