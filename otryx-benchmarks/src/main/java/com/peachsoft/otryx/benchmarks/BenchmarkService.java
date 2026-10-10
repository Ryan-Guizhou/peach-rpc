package com.peachsoft.otryx.benchmarks;

import com.peachsoft.otryx.api.OtryxRpcContract;
import com.peachsoft.otryx.api.OtryxRpcIdempotent;

/**
 * 代理调用开销基准服务。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 15:46
 */
@OtryxRpcContract
public interface BenchmarkService {

    /**
     * 返回输入字符串。
     *
     * @param value 输入
     * @return 输入字符串
     */
    @OtryxRpcIdempotent
    String echo(String value);
}
