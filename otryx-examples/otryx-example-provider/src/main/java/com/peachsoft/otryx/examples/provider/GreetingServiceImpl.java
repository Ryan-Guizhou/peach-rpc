package io.peach.rpc.examples.provider;

import io.peach.rpc.examples.api.GreetingReply;
import io.peach.rpc.examples.api.GreetingRequest;
import io.peach.rpc.examples.api.GreetingService;
import io.peach.rpc.spring.annotation.PeachRpcService;

/** 示例问候服务实现。 */
@PeachRpcService(
        interfaceClass = GreetingService.class,
        version = "1.0.0")
public class GreetingServiceImpl implements GreetingService {

    /** 创建示例服务实现。 */
    public GreetingServiceImpl() {
    }

    @Override
    public GreetingReply hello(GreetingRequest request) {
        return new GreetingReply(
                "Hello, " + request.name() + "!");
    }
}
