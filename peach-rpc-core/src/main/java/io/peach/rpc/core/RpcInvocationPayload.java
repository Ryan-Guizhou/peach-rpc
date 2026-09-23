package io.peach.rpc.core;

import java.io.Serializable;

/**
 * 远程方法参数载荷。
 *
 * @param arguments 调用参数
 */
public record RpcInvocationPayload(Object[] arguments) implements Serializable {
}
