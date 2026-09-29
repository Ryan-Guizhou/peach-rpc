package io.peach.rpc.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** 连接/Registry/TLS 恢复类 JFR Event。 */
@Name("io.peach.rpc.Recovery")
@Label("Peach RPC Recovery")
@Category({"Peach RPC", "Recovery"})
public final class RpcRecoveryJfrEvent extends Event {
    /** 恢复域。 */
    @Label("Domain")
    public String domain;
    /** 恢复动作。 */
    @Label("Action")
    public String action;
    /** 结果。 */
    @Label("Outcome")
    public String outcome;
    /** 耗时纳秒。 */
    @Label("Duration Nanos")
    public long durationNanos;
}
