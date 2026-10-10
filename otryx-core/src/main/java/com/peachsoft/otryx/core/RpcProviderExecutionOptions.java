package com.peachsoft.otryx.core;

/**
 * Provider 业务执行资源配置。
 *
 * @param allowDirect 是否允许显式 DIRECT 方法运行在 Transport Event Loop
 * @param cpuParallelism CPU 执行池线程数
 * @param cpuQueueCapacity CPU 执行池有界队列容量
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/28 10:13
 */
public record RpcProviderExecutionOptions(
        boolean allowDirect,
        int cpuParallelism,
        int cpuQueueCapacity) {

    /** 安全默认配置：关闭 DIRECT，CPU 池按可用处理器数设置。 */
    public static final RpcProviderExecutionOptions DEFAULT =
            new RpcProviderExecutionOptions(
                    false,
                    Math.max(1, Runtime.getRuntime().availableProcessors()),
                    1024);

    /** 校验执行资源配置。 */
    public RpcProviderExecutionOptions {
        if (cpuParallelism <= 0) {
            throw new IllegalArgumentException(
                    "cpuParallelism must be positive");
        }
        if (cpuQueueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "cpuQueueCapacity must be positive");
        }
    }
}
