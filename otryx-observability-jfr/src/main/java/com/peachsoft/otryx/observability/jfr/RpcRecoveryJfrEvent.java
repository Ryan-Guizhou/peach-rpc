package com.peachsoft.otryx.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** 连接/Registry/TLS 恢复类 JFR Event。 */
@Name("com.peachsoft.otryx.Recovery")
@Label("OTRYX RPC Recovery")
@Category({"OTRYX RPC", "Recovery"})
public final class RpcRecoveryJfrEvent extends Event {

    /** 创建恢复类 JFR Event。 */
    public RpcRecoveryJfrEvent() {
    }

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
