package io.peach.rpc.observability.jfr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** JFR Observer 基础测试。 */
class JfrRpcObserverTest {

    @Test
    void shouldRemainSafeWithoutActiveRecording() {
        JfrRpcObserver observer =
                new JfrRpcObserver(Duration.ofMillis(1));

        assertDoesNotThrow(() ->
                observer.onClientAttemptCompleted(
                        new ServiceKey(
                                "demo.Service",
                                "1.0.0",
                                "default"),
                        1,
                        new RpcEndpoint(
                                "127.0.0.1",
                                19090),
                        1,
                        Duration.ofMillis(2).toNanos(),
                        RpcStatus.OK,
                        null));
    }
}
