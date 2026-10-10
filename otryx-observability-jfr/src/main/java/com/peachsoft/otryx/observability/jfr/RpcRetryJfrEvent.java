package com.peachsoft.otryx.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** Consumer Retry 调度 JFR Event。 */
@Name("com.peachsoft.otryx.Retry")
@Label("OTRYX RPC Retry")
@Category({"OTRYX RPC", "Client"})
public final class RpcRetryJfrEvent extends Event {

    /** 创建 Retry JFR Event。 */
    public RpcRetryJfrEvent() {
    }

    /** 下一次 Attempt 编号。 */
    @Label("Next Attempt")
    public int nextAttempt;

    /** Retry 延迟毫秒数。 */
    @Label("Delay Millis")
    public long delayMillis;

    /** 触发 Retry 的异常类型。 */
    @Label("Cause Type")
    public String causeType;
}
