package io.peach.rpc.core;

/**
 * Provider 并发准入与在途请求字节预算。
 *
 * <p>全局请求并发仍由 `PeachRpcServer.Builder.maxConcurrent` 控制。
 * 默认将全局并发与字节额度静态平均分配到启动时已注册服务，避免
 * 某个服务抢占其他服务的全部额度。方法级默认共享所属服务额度；
 * 方法级硬上限可按需显式启用。
 *
 * @param maxInflightBytes Provider 所有已准入请求 Frame 总字节预算
 * @param maxConcurrentPerService 服务并发硬上限；0 为自动按服务数分配
 * @param maxConcurrentPerMethod 方法并发硬上限；0 为所属服务全部额度
 * @param maxInflightBytesPerService 服务请求字节硬上限；0 为自动按服务数分配
 * @param maxInflightBytesPerMethod 方法请求字节硬上限；0 为所属服务全部额度
 */
public record RpcProviderAdmissionOptions(
        long maxInflightBytes,
        int maxConcurrentPerService,
        int maxConcurrentPerMethod,
        long maxInflightBytesPerService,
        long maxInflightBytesPerMethod) {

    /** 生产默认值：256 MiB 请求 Frame 预算及自动服务隔离。 */
    public static final RpcProviderAdmissionOptions DEFAULT =
            new RpcProviderAdmissionOptions(
                    256L * 1024L * 1024L,
                    0,
                    0,
                    0L,
                    0L);

    /** 校验各层限制，0 表示由运行时计算上限。 */
    public RpcProviderAdmissionOptions {
        if (maxInflightBytes <= 0L
                || maxConcurrentPerService < 0
                || maxConcurrentPerMethod < 0
                || maxInflightBytesPerService < 0L
                || maxInflightBytesPerMethod < 0L) {
            throw new IllegalArgumentException(
                    "Provider admission budgets must be positive or zero for auto limits");
        }
    }
}
