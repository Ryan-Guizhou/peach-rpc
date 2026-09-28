package io.peach.rpc.spring.lifecycle;

import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.spring.runtime.PeachRpcRuntimeCoordinator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/**
 * 将已经创建的 Peach RPC Provider 接入 Spring 生命周期。
 */
public final class PeachRpcServerLifecycle implements SmartLifecycle {

    private final PeachRpcRuntimeCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 创建 Provider 生命周期适配器。
     *
     * @param coordinator 运行时协调器
     */
    public PeachRpcServerLifecycle(PeachRpcRuntimeCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void start() {
        coordinator.autoStartServerIfCreated().ifPresent(this::startServer);
    }

    private void startServer(PeachRpcServer server) {
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
            coordinator.serverIfCreated().ifPresent(PeachRpcServer::close);
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
