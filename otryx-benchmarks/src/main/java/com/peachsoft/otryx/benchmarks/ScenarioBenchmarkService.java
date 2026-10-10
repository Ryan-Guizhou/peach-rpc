package com.peachsoft.otryx.benchmarks;

import com.peachsoft.otryx.api.OtryxRpcContract;
import com.peachsoft.otryx.api.OtryxRpcExecution;
import com.peachsoft.otryx.api.RpcExecutionMode;

/** V2-D.2 Provider 执行与过载场景基准契约。 */
@OtryxRpcContract
public interface ScenarioBenchmarkService {

    /**
     * 极短默认阻塞型调用。
     *
     * @return 固定结果
     */
    int noOp();

    /**
     * CPU 有界执行池场景。
     *
     * @return 固定结果
     */
    @OtryxRpcExecution(RpcExecutionMode.CPU)
    int cpu();

    /**
     * 短阻塞 Virtual Thread 场景。
     *
     * @return 固定结果
     */
    int blocking();

    /**
     * 慢 Provider 场景。
     *
     * @return 固定结果
     */
    int slow();
}
