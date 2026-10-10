package com.peachsoft.otryx.observability;

/** RPC Transport 安全模式。 */
public enum RpcSecurityMode {
    /** 明文 TCP。 */
    PLAINTEXT,
    /** 单向 TLS，Consumer 校验 Provider。 */
    TLS,
    /** 双向 TLS，Consumer 与 Provider 互相校验。 */
    MTLS
}
