package io.peach.rpc.examples;

import io.peach.rpc.api.PeachRpcContract;

/**
 * 示例问候服务。
 */
@PeachRpcContract
public interface GreetingService {

    /**
     * 返回问候结果。
     *
     * @param request 问候请求
     * @return 问候响应
     */
    GreetingReply hello(GreetingRequest request);
}
