package com.peachsoft.otryx.compare.otryx;

import com.peachsoft.otryx.api.OtryxRpcContract;

/**
 * OTRYX RPC 与 Dubbo 对比使用的相同语义的二进制 Echo 契约。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/10/10 14:27
 */
@OtryxRpcContract
public interface CompareEchoService {

    /**
     * 原样返回输入字节数组。
     *
     * @param request 请求数据
     * @return 与请求数据完全一致的结果
     */
    byte[] echo(byte[] request);
}
