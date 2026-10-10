package com.peachsoft.otryx.benchmarks;

import com.peachsoft.otryx.api.OtryxRpcContract;

/** V2-D Payload 端到端基准服务。 */
@OtryxRpcContract
public interface PayloadBenchmarkService {

    /**
     * 原样返回输入 Payload。
     *
     * @param value 输入 Payload
     * @return 输入 Payload
     */
    byte[] echo(byte[] value);
}
