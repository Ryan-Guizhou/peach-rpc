package io.peach.rpc.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** Consumer 慢调用/失败 JFR Event。 */
@Name("io.peach.rpc.ClientAttempt")
@Label("Peach RPC Client Attempt")
@Category({"Peach RPC", "Client"})
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
