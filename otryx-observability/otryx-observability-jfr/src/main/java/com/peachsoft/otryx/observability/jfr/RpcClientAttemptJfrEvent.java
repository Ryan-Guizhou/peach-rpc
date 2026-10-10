package com.peachsoft.otryx.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * Consumer 慢调用/失败 JFR Event。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/29 14:59
 */
@Name("com.peachsoft.otryx.ClientAttempt")
@Label("OTRYX RPC Client Attempt")
@Category({"OTRYX RPC", "Client"})
public final class RpcClientAttemptJfrEvent extends Event {

    /** 创建 Consumer 调用 JFR Event。 */
    public RpcClientAttemptJfrEvent() {
    }

    /** 服务。 */
    @Label("Service")
    public String service;
    /** 方法 ID。 */
    @Label("Method ID")
    public int methodId;
    /** RPC 状态。 */
    @Label("Status")
    public String status;
    /** 耗时纳秒。 */
    @Label("Duration Nanos")
    public long durationNanos;
}
