package io.peach.rpc.benchmarks;

import io.peach.rpc.api.PeachRpcContract;

/** 代理调用开销基准服务。 */
@PeachRpcContract
public interface BenchmarkService {

    /**
     * 返回输入字符串。
     *
     * @param value 输入
     * @return 输入字符串
     */
    String echo(String value);
}
