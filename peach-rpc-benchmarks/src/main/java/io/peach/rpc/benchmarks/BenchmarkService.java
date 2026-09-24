package io.peach.rpc.benchmarks;

import io.peach.rpc.api.PeachRpcContract;
import io.peach.rpc.api.PeachRpcIdempotent;

/** 代理调用开销基准服务。 */
@PeachRpcContract
public interface BenchmarkService {

    /**
     * 返回输入字符串。
     *
     * @param value 输入
     * @return 输入字符串
     */
    @PeachRpcIdempotent
    String echo(String value);
}
