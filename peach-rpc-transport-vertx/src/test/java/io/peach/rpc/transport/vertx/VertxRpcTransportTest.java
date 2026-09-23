package io.peach.rpc.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.peach.rpc.transport.RpcTransportOptions;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VertxRpcTransportTest {

    @Test
    void clientAndServerShouldExchangeMultiplexedFrame()
            throws Exception {
        int port = findFreePort();
        RpcTransportOptions options = new RpcTransportOptions(
                32,
                1024 * 1024,
                1024 * 1024,
                Duration.ofSeconds(2));
        VertxRpcTransportServer server =
                new VertxRpcTransportServer(options);
        VertxRpcTransportClient client =
                new VertxRpcTransportClient(options);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", port);

        byte[] responsePayload = new byte[] {9, 8, 7};
        try {
            server.start(
                            endpoint,
                            (remote, requestBytes) ->
                                    CompletableFuture.completedFuture(
                                            response(
                                                    requestBytes,
                                                    responsePayload)))
                    .toCompletableFuture()
                    .join();

            byte[] response = client.request(
                            endpoint,
                            request(),
                            Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .join();

            RpcFrame decoded = RpcProtocolCodec.decode(response);
            assertArrayEquals(
                    responsePayload,
                    decoded.payload());
            assertTrue(decoded.requestId() > 0L);
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void shouldFailHandshakeWhenNoCommonCodecExists()
            throws Exception {
        int port = findFreePort();
        RpcTransportOptions serverOptions = options(
                Set.of(RpcCodecIds.FORY_NATIVE),
                1);
        RpcTransportOptions clientOptions = options(
                Set.of(RpcCodecIds.PROTOBUF),
                1);
        VertxRpcTransportServer server =
                new VertxRpcTransportServer(serverOptions);
        VertxRpcTransportClient client =
                new VertxRpcTransportClient(clientOptions);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", port);
        AtomicInteger handled = new AtomicInteger();

        try {
            server.start(
                            endpoint,
                            (remote, requestBytes) -> {
                                handled.incrementAndGet();
                                return CompletableFuture.completedFuture(
                                        response(
                                                requestBytes,
                                                new byte[] {1}));
                            })
                    .toCompletableFuture()
                    .join();

            CompletionException error = assertThrows(
                    CompletionException.class,
                    () -> client.request(
                                    endpoint,
                                    request(),
                                    Duration.ofSeconds(2))
                            .toCompletableFuture()
                            .join());

            assertInstanceOf(
                    RpcProtocolException.class,
                    error.getCause());
            assertEquals(0, handled.get());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    void shouldShardRequestsAcrossConfiguredConnections()
            throws Exception {
        int port = findFreePort();
        RpcTransportOptions options = options(
                Set.of(RpcCodecIds.FORY_NATIVE),
                2);
        VertxRpcTransportServer server =
                new VertxRpcTransportServer(options);
        VertxRpcTransportClient client =
                new VertxRpcTransportClient(options);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", port);
        Set<Integer> remotePorts =
                ConcurrentHashMap.newKeySet();

        try {
            server.start(
                            endpoint,
                            (remote, requestBytes) -> {
                                remotePorts.add(remote.port());
                                return CompletableFuture.completedFuture(
                                        response(
                                                requestBytes,
                                                new byte[] {1}));
                            })
                    .toCompletableFuture()
                    .join();

            for (int index = 0; index < 4; index++) {
                client.request(
                                endpoint,
                                request(),
                                Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .join();
            }

            assertEquals(2, remotePorts.size());
        } finally {
            client.close();
            server.close();
        }
    }


    @Test
    void clientShouldFailWhenHelloAckNeverArrives()
            throws Exception {
        int port = findFreePort();
        RpcTransportOptions options = new RpcTransportOptions(
                32,
                1024 * 1024,
                1024 * 1024,
                Duration.ofSeconds(2),
                Duration.ofMillis(150),
                Set.of(RpcCodecIds.FORY_NATIVE),
                1);
        VertxRpcTransportClient client =
                new VertxRpcTransportClient(options);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Thread peer = Thread.startVirtualThread(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            });

            CompletionException error = assertThrows(
                    CompletionException.class,
                    () -> client.request(
                                    endpoint,
                                    request(),
                                    Duration.ofSeconds(2))
                            .toCompletableFuture()
                            .join());

            assertInstanceOf(
                    io.peach.rpc.api.RpcTimeoutException.class,
                    error.getCause());
            peer.join();
        } finally {
            client.close();
        }
    }

    @Test
    void serverShouldCloseConnectionWhenHelloNeverArrives()
            throws Exception {
        int port = findFreePort();
        RpcTransportOptions options = new RpcTransportOptions(
                32,
                1024 * 1024,
                1024 * 1024,
                Duration.ofSeconds(2),
                Duration.ofMillis(150),
                Set.of(RpcCodecIds.FORY_NATIVE),
                1);
        VertxRpcTransportServer server =
                new VertxRpcTransportServer(options);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", port);

        try {
            server.start(
                            endpoint,
                            (remote, requestBytes) ->
                                    CompletableFuture.completedFuture(
                                            response(
                                                    requestBytes,
                                                    new byte[] {1})))
                    .toCompletableFuture()
                    .join();

            try (Socket socket = new Socket(
                    endpoint.host(),
                    endpoint.port())) {
                socket.setSoTimeout(2000);
                byte[] bytes = readUntilClosed(socket);
                RpcFrame frame = RpcProtocolCodec.decode(bytes);

                assertEquals(
                        RpcMessageType.GO_AWAY,
                        frame.messageType());
                assertEquals(
                        RpcStatus.BAD_REQUEST,
                        frame.status());
            }
        } finally {
            server.close();
        }
    }

    private static RpcTransportOptions options(
            Set<Byte> codecs,
            int connections) {
        return new RpcTransportOptions(
                32,
                1024 * 1024,
                1024 * 1024,
                Duration.ofSeconds(2),
                codecs,
                connections);
    }

    private static byte[] request() {
        return RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                RpcCodecIds.FORY_NATIVE,
                RpcStatus.OK,
                0L,
                100,
                200,
                Map.of(),
                new byte[] {1, 2, 3}));
    }

    private static byte[] response(
            byte[] requestBytes,
            byte[] payload) {
        RpcFrame request =
                RpcProtocolCodec.decode(requestBytes);
        return RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.RESPONSE,
                request.codec(),
                RpcStatus.OK,
                request.requestId(),
                request.serviceId(),
                request.methodId(),
                Map.of(),
                payload));
    }


    private static byte[] readUntilClosed(Socket socket)
            throws Exception {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        for (;;) {
            int read = socket.getInputStream().read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            output.write(buffer, 0, read);
        }
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
