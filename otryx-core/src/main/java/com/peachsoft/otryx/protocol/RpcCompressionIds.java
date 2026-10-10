package com.peachsoft.otryx.protocol;

/**
 * OTRYX RPC 官方压缩算法线协议编号。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 11:46
 */
public final class RpcCompressionIds {

    /** 不压缩。 */
    public static final byte NONE = 0;
    /** LZ4，面向低延迟场景。 */
    public static final byte LZ4 = 1;
    /** Zstandard，面向压缩率与吞吐平衡场景。 */
    public static final byte ZSTD = 2;

    private RpcCompressionIds() {
    }
}
