package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcOverloadedException;
import io.peach.rpc.api.RpcTimeoutException;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.transport.RpcTransportClient;
import io.peach.rpc.transport.RpcTransportOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Vert.x 长连接、多路复用客户端。 */
final class VertxRpcTransportClient implements RpcTransportClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxRpcTransportClient.class);
    private static final int REQUEST_ID_OFFSET = 10;

    private final Vertx vertx = Vertx.vertx();
    private final NetClient client;
    private final RpcTransportOptions options;
    private final ConcurrentMap<String, CompletableFuture<Connection>> connections =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    VertxRpcTransportClient(RpcTransportOptions options) {
        this.options = options;
        this.client = vertx.createNetClient(new NetClientOptions()
                .setConnectTimeout((int) options.connectTimeout().toMillis())
                .setTcpKeepAlive(true));
    }

    @Override
    public CompletionStage<byte[]> request(
            RpcEndpoint endpoint, long requestId, byte[] frame, Duration timeout) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException("RPC transport client is closed"));
        }
        if (frame.length > options.maxFrameBytes()) {
            return CompletableFuture.failedFuture(new RpcOverloadedException(
                    "RPC frame exceeds transport maxFrameBytes: " + frame.length));
        }
        return connection(endpoint)
                .thenCompose(connection -> connection.request(requestId, frame, timeout));
    }

    private CompletionStage<Connection> connection(RpcEndpoint endpoint) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException("RPC transport client is closed"));
        }
        String authority = endpoint.authority();
        for (;;) {
            CompletableFuture<Connection> current = connections.get(authority);
            if (current != null) {
                Connection ready = current.getNow(null);
                if (ready == null || ready.open.get()) {
                    return current;
                }
                connections.remove(authority, current);
                continue;
            }

            CompletableFuture<Connection> created = new CompletableFuture<>();
            if (connections.putIfAbsent(authority, created) != null) {
                continue;
            }

            client.connect(endpoint.port(), endpoint.host()).onComplete(result -> {
                if (result.failed()) {
                    connections.remove(authority, created);
                    created.completeExceptionally(new RpcUnavailableException(
                            "Failed to connect to " + authority, result.cause()));
                    return;
                }
                if (closed.get()) {
                    result.result().close();
                    connections.remove(authority, created);
                    created.completeExceptionally(
                            new RpcUnavailableException("RPC transport client is closed"));
                    return;
                }
                Connection connection = new Connection(endpoint, result.result());
                created.complete(connection);
            });
            return created;
        }
    }

    private final class Connection {
        private final RpcEndpoint endpoint;
        private final NetSocket socket;
        private final ConcurrentMap<Long, PendingRequest> pending = new ConcurrentHashMap<>();
        private final AtomicInteger inflight = new AtomicInteger();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final FrameAccumulator frames = new FrameAccumulator(options.maxFrameBytes());

        private Connection(RpcEndpoint endpoint, NetSocket socket) {
            this.endpoint = endpoint;
            this.socket = socket;
            socket.setWriteQueueMaxSize(options.maxWriteQueueBytes());
            socket.handler(buffer -> frames.accept(buffer, this::onFrame));
            socket.exceptionHandler(this::failAll);
            socket.closeHandler(ignored -> failAll(
                    new RpcUnavailableException("Connection closed: " + endpoint.authority())));
        }

        private CompletionStage<byte[]> request(long requestId, byte[] bytes, Duration timeout) {
            if (!open.get()) {
                return CompletableFuture.failedFuture(new RpcUnavailableException(
                        "Connection closed: " + endpoint.authority()));
            }
            if (inflight.incrementAndGet() > options.maxInflightPerConnection()) {
                inflight.decrementAndGet();
                return CompletableFuture.failedFuture(new RpcOverloadedException(
                        "Max inflight reached for " + endpoint.authority()));
            }

            CompletableFuture<byte[]> future = new CompletableFuture<>();
            long timerId = vertx.setTimer(timeout.toMillis(), ignored -> {
                PendingRequest removed = pending.remove(requestId);
                if (removed != null) {
                    inflight.decrementAndGet();
                    removed.future().completeExceptionally(
                            new RpcTimeoutException("RPC request timed out"));
                }
            });
            PendingRequest request = new PendingRequest(future, timerId);
            if (pending.putIfAbsent(requestId, request) != null) {
                vertx.cancelTimer(timerId);
                inflight.decrementAndGet();
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Duplicate request id: " + requestId));
            }

            if (socket.writeQueueFull()) {
                failPending(requestId, new RpcOverloadedException(
                        "Transport write queue is full for " + endpoint.authority()));
                return future;
            }

            socket.write(Buffer.buffer(bytes)).onFailure(error -> failPending(requestId, error));
            return future;
        }

        private void failPending(long requestId, Throwable error) {
            PendingRequest removed = pending.remove(requestId);
            if (removed != null) {
                vertx.cancelTimer(removed.timerId());
                inflight.decrementAndGet();
                removed.future().completeExceptionally(error);
            }
        }

        private void onFrame(byte[] bytes) {
            long requestId = ByteBuffer.wrap(bytes, REQUEST_ID_OFFSET, Long.BYTES).getLong();
            PendingRequest request = pending.remove(requestId);
            if (request == null) {
                LOGGER.debug(
                        "Ignoring RPC response without pending request: endpoint={}, requestId={}",
                        endpoint.authority(),
                        requestId);
                return;
            }
            vertx.cancelTimer(request.timerId());
            inflight.decrementAndGet();
            request.future().complete(bytes);
        }

        private void failAll(Throwable error) {
            if (!open.compareAndSet(true, false)) {
                return;
            }
            connections.computeIfPresent(endpoint.authority(), (key, future) ->
                    future.getNow(null) == this ? null : future);
            pending.forEach((requestId, request) -> {
                vertx.cancelTimer(request.timerId());
                request.future().completeExceptionally(error);
            });
            pending.clear();
            inflight.set(0);
            LOGGER.debug("RPC connection closed: {}", endpoint.authority());
        }

        private void close() {
            socket.close();
        }
    }

    private record PendingRequest(CompletableFuture<byte[]> future, long timerId) {}

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connections.values().forEach(future -> {
            Connection connection = future.getNow(null);
            if (connection != null) {
                connection.close();
            }
        });
        connections.clear();
        client.close();
        vertx.close();
    }
}
