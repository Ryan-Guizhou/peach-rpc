package io.peach.rpc.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider 全局/服务/方法三级准入控制器。
 *
 * <p>在 Provider 启动阶段按稳定的服务 ID 计算静态额度，各服务的并发额度
 * 与请求 Frame 字节额度合计不超过对应全局预算。热路径只使用 tryAcquire/CAS，
 * 不阻塞 Vert.x EventLoop，不依赖持久化存储。
 *
 * <p>预算仅统计已准入请求原始 Frame，不包括 Transport 排队数据、
 * 待写响应、反序列化对象图或业务对象。
 */
final class ProviderAdmissionController {

    private static final Decision GLOBAL_CONCURRENCY =
            new Decision(null, "global-concurrency");
    private static final Decision SERVICE_CONCURRENCY =
            new Decision(null, "service-concurrency");
    private static final Decision METHOD_CONCURRENCY =
            new Decision(null, "method-concurrency");
    private static final Decision GLOBAL_BYTES =
            new Decision(null, "global-inflight-bytes");
    private static final Decision SERVICE_BYTES =
            new Decision(null, "service-inflight-bytes");
    private static final Decision METHOD_BYTES =
            new Decision(null, "method-inflight-bytes");
    private static final Decision FRAME_BYTES =
            new Decision(null, "frame-exceeds-budget");

    private final int maxConcurrent;
    private final Semaphore globalConcurrency;
    private final ByteBudget globalBytes;
    private final Map<Integer, ServiceBudget> services;

    /**
     * 为启动阶段全部注册服务建立独立预算。
     *
     * @param maxConcurrent 全局最大并发数
     * @param options 额外字节预算及服务/方法上限
     * @param methodsByService 各服务已解析的方法 ID
     */
    ProviderAdmissionController(
            int maxConcurrent,
            RpcProviderAdmissionOptions options,
            Map<Integer, Set<Integer>> methodsByService) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrent must be positive");
        }
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(methodsByService, "methodsByService");
        int serviceCount = methodsByService.size();
        if (serviceCount > maxConcurrent
                || serviceCount > options.maxInflightBytes()) {
            throw new IllegalArgumentException(
                    "Each registered service requires at least one concurrency "
                            + "permit and one inflight-byte unit");
        }
        this.maxConcurrent = maxConcurrent;
        globalConcurrency = new Semaphore(maxConcurrent);
        globalBytes = new ByteBudget(options.maxInflightBytes());

        Map<Integer, ServiceBudget> resolved = new HashMap<>();
        int index = 0;
        for (Map.Entry<Integer, Set<Integer>> entry :
                new TreeMap<>(methodsByService).entrySet()) {
            int concurrencyShare = (int) share(
                    maxConcurrent,
                    serviceCount,
                    index);
            long byteShare = share(
                    options.maxInflightBytes(),
                    serviceCount,
                    index);
            int concurrencyCap = options.maxConcurrentPerService() == 0
                    ? concurrencyShare
                    : Math.min(concurrencyShare, options.maxConcurrentPerService());
            long byteCap = options.maxInflightBytesPerService() == 0L
                    ? byteShare
                    : Math.min(byteShare, options.maxInflightBytesPerService());
            int methodConcurrent = options.maxConcurrentPerMethod() == 0
                    ? concurrencyCap
                    : Math.min(concurrencyCap, options.maxConcurrentPerMethod());
            long methodBytes = options.maxInflightBytesPerMethod() == 0L
                    ? byteCap
                    : Math.min(byteCap, options.maxInflightBytesPerMethod());

            Map<Integer, MethodBudget> methods = new HashMap<>();
            for (int methodId : entry.getValue()) {
                methods.put(methodId, new MethodBudget(
                        new Semaphore(methodConcurrent),
                        new ByteBudget(methodBytes)));
            }
            resolved.put(entry.getKey(), new ServiceBudget(
                    new Semaphore(concurrencyCap),
                    new ByteBudget(byteCap),
                    Map.copyOf(methods)));
            index++;
        }
        services = Map.copyOf(resolved);
    }

    private static long share(long total, int count, int index) {
        return total / count + (index < total % count ? 1L : 0L);
    }

    /**
     * 不阻塞地尝试预留请求资源。
     *
     * @param serviceId 服务 ID
     * @param methodId 方法 ID
     * @param frameBytes 完整请求 Frame 字节数
     * @return 被接纳的 Lease 或固定低基数拒绝原因
     */
    Decision tryAcquire(
            int serviceId,
            int methodId,
            int frameBytes) {
        ServiceBudget service = services.get(serviceId);
        if (service == null) {
            throw new IllegalArgumentException(
                    "Unregistered service admission: " + serviceId);
        }
        MethodBudget method = service.methods().get(methodId);
        if (method == null) {
            throw new IllegalArgumentException(
                    "Unregistered method admission: " + methodId);
        }
        if (frameBytes <= 0
                || frameBytes > globalBytes.limit()
                || frameBytes > service.bytes().limit()
                || frameBytes > method.bytes().limit()) {
            return FRAME_BYTES;
        }

        if (!globalConcurrency.tryAcquire()) {
            return GLOBAL_CONCURRENCY;
        }
        if (!service.concurrency().tryAcquire()) {
            globalConcurrency.release();
            return SERVICE_CONCURRENCY;
        }
        if (!method.concurrency().tryAcquire()) {
            service.concurrency().release();
            globalConcurrency.release();
            return METHOD_CONCURRENCY;
        }

        if (!globalBytes.tryAcquire(frameBytes)) {
            releaseConcurrency(service, method);
            return GLOBAL_BYTES;
        }
        if (!service.bytes().tryAcquire(frameBytes)) {
            globalBytes.release(frameBytes);
            releaseConcurrency(service, method);
            return SERVICE_BYTES;
        }
        if (!method.bytes().tryAcquire(frameBytes)) {
            service.bytes().release(frameBytes);
            globalBytes.release(frameBytes);
            releaseConcurrency(service, method);
            return METHOD_BYTES;
        }

        return new Decision(
                new Lease(
                        globalConcurrency,
                        service,
                        method,
                        globalBytes,
                        frameBytes),
                null);
    }

    private void releaseConcurrency(
            ServiceBudget service,
            MethodBudget method) {
        method.concurrency().release();
        service.concurrency().release();
        globalConcurrency.release();
    }

    /** 当前全局已接纳请求的 Frame 字节数，供测试验证与调试使用。 */
    long inFlightBytes() {
        return globalBytes.used();
    }

    /** 当前全局已接纳的请求数，供测试验证与调试使用。 */
    int inFlightCalls() {
        return maxConcurrent - globalConcurrency.availablePermits();
    }

    /**
     * 准入决定。
     *
     * @param lease 成功时的独占 Lease；拒绝时为 null
     * @param reason 拒绝时低基数字符串；成功时为 null
     */
    record Decision(Lease lease, String reason) {

        /**
         * 返回是否成功预留全部资源。
         *
         * @return 成功时为 true
         */
        boolean accepted() {
            return lease != null;
        }
    }

    /** 同一请求的全层级预算一次性归还令牌，可被竞态路径重复安全调用。 */
    static final class Lease {
        private final Semaphore globalConcurrency;
        private final ServiceBudget service;
        private final MethodBudget method;
        private final ByteBudget globalBytes;
        private final int frameBytes;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(
                Semaphore globalConcurrency,
                ServiceBudget service,
                MethodBudget method,
                ByteBudget globalBytes,
                int frameBytes) {
            this.globalConcurrency = globalConcurrency;
            this.service = service;
            this.method = method;
            this.globalBytes = globalBytes;
            this.frameBytes = frameBytes;
        }

        /**
         * 最多归还一次准入资源。
         *
         * @return 首次归还为 true，后续为 false
         */
        boolean release() {
            if (!released.compareAndSet(false, true)) {
                return false;
            }
            method.bytes().release(frameBytes);
            service.bytes().release(frameBytes);
            globalBytes.release(frameBytes);
            method.concurrency().release();
            service.concurrency().release();
            globalConcurrency.release();
            return true;
        }
    }

    private record ServiceBudget(
            Semaphore concurrency,
            ByteBudget bytes,
            Map<Integer, MethodBudget> methods) {
    }

    private record MethodBudget(
            Semaphore concurrency,
            ByteBudget bytes) {
    }

    /**
     * 计量单位为 byte 的 lock-free 限额计数器，避免使用 Semaphore int 配额截断。
     */
    private static final class ByteBudget {
        private final long limit;
        private final AtomicLong used = new AtomicLong();

        private ByteBudget(long limit) {
            if (limit <= 0L) {
                throw new IllegalArgumentException(
                        "Byte budget must be positive");
            }
            this.limit = limit;
        }

        private long limit() {
            return limit;
        }

        private long used() {
            return used.get();
        }

        private boolean tryAcquire(long amount) {
            if (amount <= 0L || amount > limit) {
                return false;
            }
            for (;;) {
                long current = used.get();
                if (amount > limit - current) {
                    return false;
                }
                if (used.compareAndSet(current, current + amount)) {
                    return true;
                }
            }
        }

        private void release(long amount) {
            for (;;) {
                long previous = used.get();
                if (previous < amount) {
                    throw new IllegalStateException(
                            "Inflight-byte budget was released more than once");
                }
                if (used.compareAndSet(previous, previous - amount)) {
                    return;
                }
            }
        }
    }
}
