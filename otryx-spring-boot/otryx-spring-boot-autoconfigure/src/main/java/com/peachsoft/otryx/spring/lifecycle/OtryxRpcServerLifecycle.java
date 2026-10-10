package com.peachsoft.otryx.spring.lifecycle;

import com.peachsoft.otryx.core.OtryxRpcServer;
import com.peachsoft.otryx.spring.runtime.OtryxRpcRuntimeCoordinator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/**
 * 将已经创建的 OTRYX RPC Provider 接入 Spring 生命周期。
 */
public final class OtryxRpcServerLifecycle implements SmartLifecycle {

    private final OtryxRpcRuntimeCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 创建 Provider 生命周期适配器。
     *
     * @param coordinator 运行时协调器
     */
    public OtryxRpcServerLifecycle(OtryxRpcRuntimeCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void start() {
        coordinator.autoStartServerIfCreated().ifPresent(this::startServer);
    }

    private void startServer(OtryxRpcServer server) {
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
            coordinator.serverIfCreated().ifPresent(OtryxRpcServer::close);
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
