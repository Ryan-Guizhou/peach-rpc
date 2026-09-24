package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcOverloadedException;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcTimeoutException;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcErrorCodec;
import io.peach.rpc.protocol.RpcFeature;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcHandshakeCodec;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcNegotiatedCapabilities;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.peach.rpc.transport.RpcTransportClient;
import io.peach.rpc.transport.RpcTransportOptions;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vert.x 长连接、多路复用客户端。
 *
 * <p>每个连接的 pending table、inflight 与帧累积器都只在所属 Event Loop
 * 上访问，避免请求完成路径上的共享 ConcurrentHashMap 与原子计数竞争。
 */
final class VertxRpcTransportClient implements RpcTransportClient {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(VertxRpcTransportClient.class);
    private static final int MESSAGE_TYPE_OFFSET = 6;
    private static final int REQUEST_ID_OFFSET = 10;
    private static final int CODEC_OFFSET = 7;

    private final Vertx vertx = Vertx.vertx();
    private final NetClient client;
    private final RpcTransportOptions options;
    private final ConcurrentMap<RpcEndpoint, ConnectionGroup> groups =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    VertxRpcTransportClient(RpcTransportOptions options) {
        this.options = options;
        this.client = vertx.createNetClient(
                new NetClientOptions()
                        .setConnectTimeout(
                                (int) options.connectTimeout().toMillis())
                        .setTcpKeepAlive(true)
                        .setTcpNoDelay(true));
    }

    @Override
    public CompletionStage<byte[]> request(
            RpcEndpoint endpoint,
            byte[] frame,
            Duration timeout) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException(
                            "RPC transport client is closed"));
        }
        if (frame.length > options.maxFrameBytes()) {
            return CompletableFuture.failedFuture(
                    new RpcOverloadedException(
                            "RPC frame exceeds transport maxFrameBytes: "
                                    + frame.length));
        }
        ConnectionGroup group = groups.computeIfAbsent(
                endpoint,
                ConnectionGroup::new);
        return group.request(frame, timeout);
    }

    private final class ConnectionGroup {
        private final RpcEndpoint endpoint;
        private final AtomicReferenceArray<CompletableFuture<Connection>> slots;
        private final ThreadLocal<Integer> cursor;

        private ConnectionGroup(RpcEndpoint endpoint) {
            this.endpoint = endpoint;
            this.slots = new AtomicReferenceArray<>(
                    options.connectionsPerEndpoint());
            this.cursor = ThreadLocal.withInitial(() ->
                    Math.floorMod(
                            Long.hashCode(Thread.currentThread().threadId()),
                            slots.length()));
        }

        private CompletionStage<byte[]> request(
                byte[] frame,
                Duration timeout) {
            int current = cursor.get();
            int index = current % slots.length();
            cursor.set(current == Integer.MAX_VALUE ? 0 : current + 1);
            return connection(index)
                    .thenCompose(connection ->
                            connection.request(
                                    frame,
                                    timeout));
        }

        private CompletionStage<Connection> connection(int index) {
            for (;;) {
                CompletableFuture<Connection> current = slots.get(index);
                if (current != null) {
                    Connection ready = current.getNow(null);
                    if (ready == null || ready.open) {
                        return current;
                    }
                    slots.compareAndSet(index, current, null);
                    continue;
                }

                CompletableFuture<Connection> created =
                        new CompletableFuture<>();
                if (!slots.compareAndSet(index, null, created)) {
                    continue;
                }
                connect(index, created);
                return created;
            }
        }

        private void connect(
                int index,
                CompletableFuture<Connection> created) {
            client.connect(
                    endpoint.port(),
                    endpoint.host())
                    .onComplete(result -> {
                        if (result.failed()) {
                            slots.compareAndSet(index, created, null);
                            created.completeExceptionally(
                                    new RpcUnavailableException(
                                            "Failed to connect to "
                                                    + endpoint.authority(),
                                            result.cause()));
                            return;
                        }
                        if (closed.get()) {
                            result.result().close();
                            slots.compareAndSet(index, created, null);
                            created.completeExceptionally(
                                    new RpcUnavailableException(
                                            "RPC transport client is closed"));
                            return;
                        }

                        Connection connection = new Connection(
                                this,
                                index,
                                endpoint,
                                result.result());
                        connection.handshake()
                                .whenComplete((ignored, error) -> {
                                    if (error != null) {
                                        slots.compareAndSet(
                                                index,
                                                created,
                                                null);
                                        connection.close();
                                        created.completeExceptionally(error);
                                    } else {
                                        created.complete(connection);
                                    }
                                });
                    });
        }

        private void release(
                int index,
                Connection connection) {
            CompletableFuture<Connection> current = slots.get(index);
            if (current != null
                    && current.getNow(null) == connection) {
                slots.compareAndSet(index, current, null);
            }
        }

        private void close() {
            for (int index = 0; index < slots.length(); index++) {
                CompletableFuture<Connection> future = slots.get(index);
                if (future == null) {
                    continue;
                }
                Connection connection = future.getNow(null);
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }

    private final class Connection {
        private final ConnectionGroup group;
        private final int slot;
        private final RpcEndpoint endpoint;
        private final NetSocket socket;
        private final Context context;
        private final Map<Long, PendingRequest> pending = new HashMap<>();
        private final FrameAccumulator frames =
                new FrameAccumulator(options.maxFrameBytes());
        private final CompletableFuture<RpcNegotiatedCapabilities> handshake =
                new CompletableFuture<>();

        private boolean open = true;
        private boolean draining;
        private int inflight;
        private long nextRequestId;
        private RpcNegotiatedCapabilities negotiated;
        private long handshakeTimerId = -1L;

        private Connection(
                ConnectionGroup group,
                int slot,
                RpcEndpoint endpoint,
                NetSocket socket) {
            this.group = group;
            this.slot = slot;
            this.endpoint = endpoint;
            this.socket = socket;
            this.context = Vertx.currentContext();
            if (context == null) {
                throw new IllegalStateException(
                        "Vert.x connection must be created on an event loop");
            }

            socket.setWriteQueueMaxSize(
                    options.maxWriteQueueBytes());
            socket.handler(buffer ->
                    frames.accept(buffer, this::onFrame));
            socket.exceptionHandler(error ->
                    failAll(new RpcUnavailableException(
                            "Connection failed: "
                                    + endpoint.authority(),
                            error)));
            socket.closeHandler(ignored -> {
                if (open) {
                    failAll(new RpcUnavailableException(
                            "Connection closed by remote: "
                                    + endpoint.authority()));
                }
            });
            handshakeTimerId = vertx.setTimer(
                    options.handshakeTimeout().toMillis(),
                    ignored -> failAll(new RpcTimeoutException(
                            "RPC handshake timed out for "
                                    + endpoint.authority())));
            sendHello();
        }

        private CompletionStage<RpcNegotiatedCapabilities> handshake() {
            return handshake;
        }

        private void sendHello() {
            RpcFrame hello = new RpcFrame(
                    RpcMessageType.HELLO,
                    RpcCodecIds.CONTROL,
                    RpcStatus.OK,
                    0L,
                    0,
                    0,
                    Map.of(),
                    RpcHandshakeCodec.encode(
                            options.capabilities()));
            socket.write(Buffer.buffer(
                            RpcProtocolCodec.encode(hello)))
                    .onFailure(this::failAll);
        }

        private CompletionStage<byte[]> request(
                byte[] bytes,
                Duration timeout) {
            CompletableFuture<byte[]> result = new CompletableFuture<>();
            if (Vertx.currentContext() == context) {
                requestOnEventLoop(
                        bytes,
                        timeout,
                        result);
            } else {
                context.runOnContext(ignored ->
                        requestOnEventLoop(
                                bytes,
                                timeout,
                                result));
            }
            return result;
        }

        private void requestOnEventLoop(
                byte[] bytes,
                Duration timeout,
                CompletableFuture<byte[]> result) {
            if (result.isDone()) {
                return;
            }
            if (!open || draining) {
                result.completeExceptionally(
                        new RpcUnavailableException(
                                "Connection closed: "
                                        + endpoint.authority()));
                return;
            }
            if (bytes.length > negotiated.maxFrameBytes()) {
                result.completeExceptionally(
                        new RpcOverloadedException(
                                "RPC frame exceeds negotiated maxFrameBytes"));
                return;
            }
            byte codecId = bytes[CODEC_OFFSET];
            if (!negotiated.codecIds().contains(codecId)) {
                result.completeExceptionally(
                        new RpcProtocolException(
                                "RPC codec was not negotiated: "
                                        + Byte.toUnsignedInt(codecId)));
                return;
            }
            if (inflight >= options.maxInflightPerConnection()) {
                result.completeExceptionally(
                        new RpcOverloadedException(
                                "Max inflight reached for "
                                        + endpoint.authority()));
                return;
            }
            if (socket.writeQueueFull()) {
                result.completeExceptionally(
                        new RpcOverloadedException(
                                "Transport write queue is full for "
                                        + endpoint.authority()));
                return;
            }
            long requestId = nextRequestId();
            RpcProtocolCodec.writeRequestId(bytes, requestId);

            inflight++;
            long timerId = vertx.setTimer(
                    timeout.toMillis(),
                    ignored -> timeout(requestId));
            pending.put(
                    requestId,
                    new PendingRequest(result, timerId));
            result.whenComplete((ignoredValue, ignoredError) -> {
                if (result.isCancelled()) {
                    context.runOnContext(ignored ->
                            cancelPending(requestId));
                }
            });
            socket.write(Buffer.buffer(bytes))
                    .onFailure(error ->
                            failPending(requestId, error));
        }

        private long nextRequestId() {
            long value = ++nextRequestId;
            if (value == 0L) {
                value = ++nextRequestId;
            }
            return value;
        }

        private void timeout(long requestId) {
            PendingRequest removed = pending.remove(requestId);
            if (removed == null) {
                return;
            }
            inflight--;
            sendCancel(requestId);
            removed.future().completeExceptionally(
                    new RpcTimeoutException(
                            "RPC request timed out"));
            closeIfDrained();
        }

        private void cancelPending(long requestId) {
            PendingRequest removed = pending.remove(requestId);
            if (removed == null) {
                return;
            }
            vertx.cancelTimer(removed.timerId());
            inflight--;
            sendCancel(requestId);
            closeIfDrained();
        }

        private void sendCancel(long requestId) {
            if (!open
                    || negotiated == null
                    || !negotiated.features().contains(RpcFeature.CANCEL)) {
                return;
            }
            socket.write(Buffer.buffer(
                            RpcProtocolCodec.encodeCancel(requestId)))
                    .onFailure(this::failAll);
        }

        private void failPending(
                long requestId,
                Throwable error) {
            PendingRequest removed = pending.remove(requestId);
            if (removed == null) {
                return;
            }
            vertx.cancelTimer(removed.timerId());
            inflight--;
            removed.future().completeExceptionally(error);
        }

        private void onFrame(byte[] bytes) {
            if (bytes[MESSAGE_TYPE_OFFSET] == RpcMessageType.GO_AWAY.code()) {
                handleGoAway(bytes);
                return;
            }
            if (!handshake.isDone()) {
                handleHandshake(bytes);
                return;
            }

            long requestId = ByteBuffer.wrap(
                            bytes,
                            REQUEST_ID_OFFSET,
                            Long.BYTES)
                    .getLong();
            PendingRequest request = pending.remove(requestId);
            if (request == null) {
                LOGGER.debug(
                        "Ignoring RPC response without pending request: "
                                + "endpoint={}, requestId={}",
                        endpoint.authority(),
                        requestId);
                return;
            }
            vertx.cancelTimer(request.timerId());
            inflight--;
            request.future().complete(bytes);
            closeIfDrained();
        }

        private void handleGoAway(byte[] bytes) {
            try {
                RpcFrame frame = RpcProtocolCodec.decode(bytes);
                var error = RpcErrorCodec.decode(frame.payload());
                if (frame.status() != RpcStatus.UNAVAILABLE) {
                    failAll(new RpcProtocolException(
                            "Remote GO_AWAY: " + error.message()));
                    return;
                }
                draining = true;
                group.release(slot, this);
                LOGGER.debug(
                        "RPC connection is draining: endpoint={}, reason={}",
                        endpoint.authority(),
                        error.message());
                closeIfDrained();
            } catch (Throwable error) {
                failAll(error);
            }
        }

        private void closeIfDrained() {
            if (draining && pending.isEmpty() && open) {
                socket.close();
            }
        }

        private void handleHandshake(byte[] bytes) {
            try {
                RpcFrame frame = RpcProtocolCodec.decode(bytes);
                if (frame.messageType() != RpcMessageType.HELLO_ACK
                        || frame.requestId() != 0L
                        || frame.codec() != RpcCodecIds.CONTROL) {
                    throw new RpcProtocolException(
                            "Expected HELLO_ACK as first server frame");
                }
                var serverCapabilities =
                        RpcHandshakeCodec.decode(frame.payload());
                negotiated = RpcHandshakeCodec.negotiate(
                        options.capabilities(),
                        serverCapabilities);
                cancelHandshakeTimer();
                if (open && !handshake.isDone()) {
                    handshake.complete(negotiated);
                }
            } catch (Throwable error) {
                handshake.completeExceptionally(error);
                failAll(error);
            }
        }

        private void cancelHandshakeTimer() {
            if (handshakeTimerId >= 0L) {
                vertx.cancelTimer(handshakeTimerId);
                handshakeTimerId = -1L;
            }
        }

        private void failAll(Throwable error) {
            if (Vertx.currentContext() != context) {
                context.runOnContext(ignored -> failAll(error));
                return;
            }
            if (!open) {
                return;
            }
            open = false;
            group.release(slot, this);
            pending.values().forEach(request -> {
                vertx.cancelTimer(request.timerId());
                request.future().completeExceptionally(error);
            });
            pending.clear();
            inflight = 0;
            cancelHandshakeTimer();
            if (!handshake.isDone()) {
                handshake.completeExceptionally(error);
            }
            LOGGER.debug(
                    "RPC connection closed: {}",
                    endpoint.authority());
            socket.close();
        }

        private void close() {
            if (Vertx.currentContext() == context) {
                cancelHandshakeTimer();
                socket.close();
            } else {
                context.runOnContext(ignored -> {
                    cancelHandshakeTimer();
                    socket.close();
                });
            }
        }
    }

    private record PendingRequest(
            CompletableFuture<byte[]> future,
            long timerId) {
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        groups.values().forEach(ConnectionGroup::close);
        groups.clear();
        client.close();
        vertx.close();
    }
}
