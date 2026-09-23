package io.peach.rpc.examples;

import java.io.Serializable;

/**
 * 问候请求。
 *
 * @param name 问候对象名称
 */
public record GreetingRequest(String name) implements Serializable {
}
