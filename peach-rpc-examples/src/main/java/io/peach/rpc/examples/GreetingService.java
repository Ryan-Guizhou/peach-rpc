package io.peach.rpc.examples;

/**
 * 示例问候服务。
 */
public interface GreetingService {

    /**
     * 返回问候结果。
     *
     * @param request 问候请求
     * @return 问候响应
     */
    GreetingReply hello(GreetingRequest request);
}
