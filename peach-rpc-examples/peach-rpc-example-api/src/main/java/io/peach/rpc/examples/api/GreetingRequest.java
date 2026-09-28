package io.peach.rpc.examples.api;

import java.io.Serializable;

/**
 * 问候请求。
 *
 * @param name 问候对象名称
 */
public record GreetingRequest(String name) implements Serializable {
}
