package io.peach.rpc.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcTimeoutException;
import io.peach.rpc.protocol.RpcProtocolException;
import org.junit.jupiter.api.Test;

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
