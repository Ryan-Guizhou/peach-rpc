package io.peach.rpc.examples;

import java.io.Serializable;

/**
 * 问候响应。
 *
 * @param message 问候结果
 */
public record GreetingReply(String message) implements Serializable {
}
