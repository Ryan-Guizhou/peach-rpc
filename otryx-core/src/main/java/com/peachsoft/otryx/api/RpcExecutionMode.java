package com.peachsoft.otryx.api;

/**
 * Provider 业务方法的执行资源类型。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/28 10:13
 */
public enum RpcExecutionMode {

    /**
     * 默认阻塞型业务执行模式。
     *
     * <p>每个请求提交到虚拟线程，适合 JDBC、文件、同步 HTTP/SDK 等可能阻塞的业务。
     */
    BLOCKING_VIRTUAL,

    /**
     * CPU 密集型执行模式。
     *
     * <p>请求提交到有界固定线程池，避免大量 CPU 密集任务占满虚拟线程调度器和机器核心。
     */
    CPU,

    /**
     * 直接在 Transport Event Loop 上执行。
     *
     * <p>只允许极短、确定不阻塞的纯内存逻辑。该模式默认关闭，必须由 Provider 显式允许。
     */
    DIRECT
}
