package com.peachsoft.otryx.observability;

/** TLS 证书热更新结果。 */
public enum RpcCertificateReloadOutcome {
    /** 新证书完成校验并生效。 */
    SUCCESS,
    /** 新证书无效，继续使用旧证书。 */
    FAILURE
}
