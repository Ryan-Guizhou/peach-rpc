package com.peachsoft.otryx.examples.provider;

import com.peachsoft.otryx.examples.api.GreetingReply;
import com.peachsoft.otryx.examples.api.GreetingRequest;
import com.peachsoft.otryx.examples.api.GreetingService;
import com.peachsoft.otryx.spring.annotation.OtryxRpcService;

/** 示例问候服务实现。 */
@OtryxRpcService(
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
