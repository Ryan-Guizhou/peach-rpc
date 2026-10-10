package com.peachsoft.otryx.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.peachsoft.otryx.api.RpcStatus;
import com.peachsoft.otryx.api.RpcTimeoutException;
import com.peachsoft.otryx.protocol.RpcProtocolException;
import org.junit.jupiter.api.Test;

/**
 * 验证 RPC 失败分类及异常归因规则。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 11:21
 */
class RpcFailureClassifierTest {

    @Test
    void shouldClassifyProtocolFailure() {
        assertEquals(
                RpcFailureCategory.PROTOCOL,
                RpcFailureClassifier.classify(
                        RpcStatus.BAD_REQUEST,
                        new RpcProtocolException("bad")));
    }

    @Test
    void shouldClassifyTimeoutFailure() {
        assertEquals(
                RpcFailureCategory.TRANSPORT,
                RpcFailureClassifier.classify(
                        RpcStatus.DEADLINE_EXCEEDED,
                        new RpcTimeoutException("timeout")));
    }

    @Test
    void shouldClassifySuccess() {
        assertEquals(
                RpcFailureCategory.NONE,
                RpcFailureClassifier.classify(
                        RpcStatus.OK,
                        null));
    }
}
