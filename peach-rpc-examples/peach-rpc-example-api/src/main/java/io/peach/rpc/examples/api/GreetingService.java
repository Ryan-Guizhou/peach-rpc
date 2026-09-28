package io.peach.rpc.examples.api;

import io.peach.rpc.api.PeachRpcContract;
import io.peach.rpc.api.PeachRpcIdempotent;

/** 示例问候 RPC 契约。 */
@PeachRpcContract
public interface GreetingService {

    /**
     * 返回问候结果。
     *
     * @param request 问候请求
     * @return 问候响应
     */
    @PeachRpcIdempotent
    GreetingReply hello(GreetingRequest request);
}
