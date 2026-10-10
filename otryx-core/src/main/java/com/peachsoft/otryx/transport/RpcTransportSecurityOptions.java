package com.peachsoft.otryx.transport;

import com.peachsoft.otryx.observability.RpcSecurityMode;
import java.time.Duration;

/**
 * Transport TLS/mTLS 实现无关配置。
 *
 * @param mode 安全模式
 * @param certificatePath PEM 证书路径
 * @param privateKeyPath PEM 私钥路径
 * @param trustCertificatePath PEM CA/信任证书路径
 * @param hostnameVerification Consumer 是否校验 Provider 主机名
 * @param handshakeTimeout TLS 握手超时
 * @param reloadInterval 证书文件变更检查周期
 * @param expiryWarningThreshold 证书过期前告警窗口
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 14:54
 */
public record RpcTransportSecurityOptions(
        RpcSecurityMode mode,
        String certificatePath,
        String privateKeyPath,
        String trustCertificatePath,
        boolean hostnameVerification,
        Duration handshakeTimeout,
        Duration reloadInterval,
        Duration expiryWarningThreshold) {

    /** 默认明文 Transport 安全配置。 */
    public static final RpcTransportSecurityOptions PLAINTEXT =
            new RpcTransportSecurityOptions(
                    RpcSecurityMode.PLAINTEXT,
                    "",
                    "",
                    "",
                    true,
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(30),
                    Duration.ofDays(7));

    /** 规范化并校验 Transport Security 参数。 */
    public RpcTransportSecurityOptions {
        mode = mode == null
                ? RpcSecurityMode.PLAINTEXT
                : mode;
        certificatePath = normalize(certificatePath);
        privateKeyPath = normalize(privateKeyPath);
        trustCertificatePath =
                normalize(trustCertificatePath);
        requirePositive(
                handshakeTimeout,
                "handshakeTimeout");
        requirePositive(
                reloadInterval,
                "reloadInterval");
        requirePositive(
                expiryWarningThreshold,
                "expiryWarningThreshold");
    }

    /**
     * 返回是否启用 TLS。
     *
     * @return TLS/MTLS 时返回 true
     */
    public boolean enabled() {
        return mode != RpcSecurityMode.PLAINTEXT;
    }

    /**
     * 返回是否启用双向 TLS。
     *
     * @return MTLS 时返回 true
     */
    public boolean mutualTls() {
        return mode == RpcSecurityMode.MTLS;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requirePositive(
            Duration value,
            String name) {
        if (value == null
                || value.isNegative()
                || value.isZero()) {
            throw new IllegalArgumentException(
                    name + " must be positive");
        }
    }
}
