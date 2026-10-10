package com.peachsoft.otryx.observability;

/** RPC 失败的低基数运维分类。 */
public enum RpcFailureCategory {
    /** 非失败。 */
    NONE,
    /** Consumer 输入、调用方式或取消。 */
    CLIENT,
    /** Provider 业务或执行资源问题。 */
    PROVIDER,
    /** 网络连接、超时或端点不可用。 */
    TRANSPORT,
    /** Registry 控制面问题。 */
    REGISTRY,
    /** TLS/mTLS 与证书问题。 */
    SECURITY,
    /** 线协议或兼容性问题。 */
    PROTOCOL,
    /** Retry/Circuit/Outlier 等容错行为。 */
    RESILIENCE,
    /** 无法可靠归类。 */
    UNKNOWN
}
