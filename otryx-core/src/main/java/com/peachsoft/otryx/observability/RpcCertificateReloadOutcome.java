package com.peachsoft.otryx.observability;

/**
 * TLS 证书热更新结果。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 14:42
 */
public enum RpcCertificateReloadOutcome {
    /** 新证书完成校验并生效。 */
    SUCCESS,
    /** 新证书无效，继续使用旧证书。 */
    FAILURE
}
