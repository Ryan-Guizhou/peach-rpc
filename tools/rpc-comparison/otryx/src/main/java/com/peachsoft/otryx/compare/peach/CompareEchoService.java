package io.peach.rpc.compare.peach;

import io.peach.rpc.api.PeachRpcContract;

/** Peach RPC 与 Dubbo 对比使用的相同语义的二进制 Echo 契约。 */
@PeachRpcContract
public interface CompareEchoService {

    /**
     * 原样返回输入字节数组。
     *
     * @param request 请求数据
     * @return 与请求数据完全一致的结果
     */
    byte[] echo(byte[] request);
}
