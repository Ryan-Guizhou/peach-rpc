package io.peach.rpc.compare.dubbo;

/** Dubbo 独立 JVM 中使用的相同 byte[] Echo 业务接口。 */
public interface CompareEchoService {

    /**
     * 原样返回输入数据。
     *
     * @param request 请求 Payload
     * @return 与请求相同的 Payload
     */
    byte[] echo(byte[] request);
}
