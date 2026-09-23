package io.peach.rpc.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.transport.RpcTransportOptions;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VertxRpcTransportTest {
    @Test
    void clientAndServerShouldExchangeMultiplexedFrame() throws Exception {
        int port = findFreePort();
        RpcTransportOptions options = new RpcTransportOptions(
                32, 1024 * 1024, 1024 * 1024, Duration.ofSeconds(2));
        VertxRpcTransportServer server = new VertxRpcTransportServer(options);
        VertxRpcTransportClient client = new VertxRpcTransportClient(options);
        RpcEndpoint endpoint = new RpcEndpoint("127.0.0.1", port);

        byte[] responsePayload = new byte[] {9, 8, 7};
        try {
            server.start(endpoint, (remote, requestBytes) -> {
                RpcFrame request = RpcProtocolCodec.decode(requestBytes);
                RpcFrame response = new RpcFrame(
                        RpcMessageType.RESPONSE,
                        request.codec(),
                        RpcStatus.OK,
                        request.requestId(),
                        request.serviceId(),
                        request.methodId(),
                        Map.of(),
                        responsePayload);
                return java.util.concurrent.CompletableFuture.completedFuture(
                        RpcProtocolCodec.encode(response));
            }).toCompletableFuture().join();

            RpcFrame request = new RpcFrame(
                    RpcMessageType.REQUEST,
                    (byte) 1,
                    RpcStatus.OK,
                    42L,
                    100,
                    200,
                    Map.of(),
                    new byte[] {1, 2, 3});
            byte[] response = client.request(
                            endpoint,
                            42L,
                            RpcProtocolCodec.encode(request),
                            Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .join();

            assertArrayEquals(responsePayload, RpcProtocolCodec.decode(response).payload());
        } finally {
            client.close();
            server.close();
        }
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
