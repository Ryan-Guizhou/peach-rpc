package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcOverloadedException;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcTimeoutException;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.observability.RpcCertificateReloadOutcome;
import io.peach.rpc.observability.RpcConnectionCloseReason;
import io.peach.rpc.observability.RpcConnectionRole;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
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
    private static final int CODEC_OFFSET = 7;

    private final Vertx vertx = Vertx.vertx();
    private final NetClient client;
    private final RpcTransportOptions options;
    private final ConcurrentMap<RpcEndpoint, ConnectionGroup> groups =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long tlsReloadTimerId = -1L;
    private volatile VertxTlsSupport.FileState tlsFileState;

    VertxRpcTransportClient(RpcTransportOptions options) {
        this.options = options;
        NetClientOptions clientOptions =
                new NetClientOptions()
                        .setConnectTimeout(
                                (int) options.connectTimeout().toMillis())
                        .setTcpKeepAlive(true)
                        .setTcpNoDelay(true);
        VertxTlsSupport.configureClient(
                clientOptions,
                options.security(),
                options.observer());
        this.client = vertx.createNetClient(clientOptions);
        startTlsReload();
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
        private final AtomicIntegerArray reconnectAttempts;
        private final ThreadLocal<Integer> cursor;

        private ConnectionGroup(RpcEndpoint endpoint) {
            this.endpoint = endpoint;
            this.slots = new AtomicReferenceArray<>(
                    options.connectionsPerEndpoint());
            this.reconnectAttempts = new AtomicIntegerArray(
                    options.connectionsPerEndpoint());
            this.cursor = ThreadLocal.withInitial(() ->
                    Math.floorMod(
                            Long.hashCode(Thread.currentThread().threadId()),
                            slots.length()));
        }

        private CompletionStage<byte[]> request(
                byte[] frame,
                Duration timeout) {
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            int current = cursor.get();
            int index = current % slots.length();
            cursor.set(current == Integer.MAX_VALUE ? 0 : current + 1);

            CompletableFuture<byte[]> result = new CompletableFuture<>();
            connection(index).whenComplete((connection, connectError) -> {
                if (connectError != null) {
                    result.completeExceptionally(connectError);
                    return;
                }
                if (result.isCancelled()) {
                    return;
                }
                long remainingNanos =
                        deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    result.completeExceptionally(
                            new RpcTimeoutException(
                                    "RPC request timed out while connecting to "
                                            + endpoint.authority()));
                    return;
                }

                CompletableFuture<byte[]> request = connection
                        .request(
                                frame,
                                Duration.ofNanos(remainingNanos))
                        .toCompletableFuture();
                result.whenComplete((ignoredValue, ignoredError) -> {
                    if (result.isCancelled()) {
                        request.cancel(true);
                    }
                });
                request.whenComplete((response, requestError) -> {
                    if (result.isDone()) {
                        return;
                    }
                    if (requestError != null) {
                        result.completeExceptionally(requestError);
                    } else {
                        result.complete(response);
                    }
                });
            });
            return result;
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
            int reconnectAttempt =
                    reconnectAttempts.get(index);
            long delayMillis =
                    reconnectDelayMillis(reconnectAttempt);
            if (reconnectAttempt > 0
                    && options.observer().enabled()) {
                options.observer().onConnectionReconnectScheduled(
                        endpoint,
                        reconnectAttempt,
                        delayMillis);
            }
            if (delayMillis <= 0L) {
                doConnect(index, created);
                return;
            }
            vertx.setTimer(delayMillis, ignored -> {
                if (closed.get() || slots.get(index) != created) {
                    slots.compareAndSet(index, created, null);
                    created.completeExceptionally(
                            new RpcUnavailableException(
                                    "RPC transport client is closed"));
                    return;
                }
                doConnect(index, created);
            });
        }

        private void doConnect(
                int index,
                CompletableFuture<Connection> created) {
            long connectStartedNanos = System.nanoTime();
            client.connect(
                    endpoint.port(),
                    endpoint.host())
                    .onComplete(result -> {
                        if (options.security().enabled()
                                && options.observer().enabled()) {
                            options.observer()
                                    .onTlsHandshakeCompleted(
                                            RpcConnectionRole.CLIENT,
                                            endpoint,
                                            options.security().mode(),
                                            System.nanoTime()
                                                    - connectStartedNanos,
                                            result.failed()
                                                    ? result.cause()
                                                    : null);
                        }
                        if (result.failed()) {
                            recordFailure(index);
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
                                result.result(),
                                connectStartedNanos);
                        connection.handshake()
                                .whenComplete((ignored, error) -> {
                                    if (error != null) {
                                        recordFailure(index);
                                        slots.compareAndSet(
                                                index,
                                                created,
                                                null);
                                        connection.close();
                                        created.completeExceptionally(error);
                                    } else {
                                        reconnectAttempts.set(index, 0);
                                        if (options.observer().enabled()) {
                                            options.observer()
                                                    .onConnectionEstablished(
                                                            RpcConnectionRole.CLIENT,
                                                            endpoint,
                                                            System.nanoTime()
                                                                    - connectStartedNanos);
                                        }
                                        created.complete(connection);
                                    }
                                });
                    });
        }

        private void failed(
                int index,
                Connection connection) {
            release(index, connection);
            recordFailure(index);
        }

        private void recordFailure(int index) {
            reconnectAttempts.updateAndGet(
                    index,
                    current -> Math.min(current + 1, 30));
        }

        private long reconnectDelayMillis(int failures) {
            if (failures <= 0) {
                return 0L;
            }
            long base = options.reconnectBaseBackoff().toMillis();
            long max = options.reconnectMaxBackoff().toMillis();
            int shift = Math.min(failures - 1, 20);
            long ceiling = base > (Long.MAX_VALUE >> shift)
                    ? max
                    : Math.min(max, base << shift);
            return ceiling <= 1L
                    ? ceiling
                    : ThreadLocalRandom.current().nextLong(ceiling + 1L);
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
        private long heartbeatTimerId = -1L;
        private long lastReadNanos = System.nanoTime();
        private long pingSentAtNanos;
        private boolean closeObserved;

        private Connection(
                ConnectionGroup group,
                int slot,
                RpcEndpoint endpoint,
                NetSocket socket,
                long connectStartedNanos) {
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
                    failAll(
                            new RpcUnavailableException(
                                    "Connection failed: "
                                            + endpoint.authority(),
                                    error),
                            RpcConnectionCloseReason.TRANSPORT_ERROR));
            socket.closeHandler(ignored -> {
                if (!open) {
                    return;
                }
                if (draining) {
                    closeGracefully(
                            RpcConnectionCloseReason.GO_AWAY);
                } else {
                    failAll(
                            new RpcUnavailableException(
                                    "Connection closed by remote: "
                                            + endpoint.authority()),
                            RpcConnectionCloseReason.REMOTE_CLOSE);
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
            RpcProtocolCodec.rewriteTimeoutBudgetMillis(
                    bytes,
                    Math.max(1L, timeout.toMillis()));

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
            lastReadNanos = System.nanoTime();
            pingSentAtNanos = 0L;
            if (bytes[MESSAGE_TYPE_OFFSET] == RpcMessageType.GO_AWAY.code()) {
                handleGoAway(bytes);
                return;
            }
            if (!handshake.isDone()) {
                handleHandshake(bytes);
                return;
            }
            byte messageType = bytes[MESSAGE_TYPE_OFFSET];
            if (messageType == RpcMessageType.PING.code()
                    || messageType == RpcMessageType.PONG.code()) {
                handleHeartbeat(bytes);
                return;
            }

            long requestId =
                    RpcProtocolCodec.readRequestId(bytes);
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
                cancelHeartbeatTimer();
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
                    startHeartbeat();
                }
            } catch (Throwable error) {
                handshake.completeExceptionally(error);
                failAll(error);
            }
        }

        private void startHeartbeat() {
            if (!negotiated.features().contains(RpcFeature.HEARTBEAT)) {
                return;
            }
            long intervalMillis = options.heartbeatInterval().toMillis();
            long timeoutMillis = options.heartbeatTimeout().toMillis();
            long tickMillis = Math.max(
                    1L,
                    Math.min(intervalMillis, timeoutMillis));
            heartbeatTimerId = vertx.setPeriodic(
                    tickMillis,
                    ignored -> heartbeatTick());
        }

        private void heartbeatTick() {
            if (!open || draining || negotiated == null) {
                return;
            }
            long now = System.nanoTime();
            if (pingSentAtNanos > 0L) {
                if (now - pingSentAtNanos
                        >= options.heartbeatTimeout().toNanos()) {
                    if (options.observer().enabled()) {
                        options.observer()
                                .onConnectionHeartbeatTimeout(
                                        RpcConnectionRole.CLIENT,
                                        endpoint,
                                        now - pingSentAtNanos);
                    }
                    failAll(
                            new RpcTimeoutException(
                                    "RPC heartbeat timed out for "
                                            + endpoint.authority()),
                            RpcConnectionCloseReason.HEARTBEAT_TIMEOUT);
                }
                return;
            }
            if (now - lastReadNanos
                    < options.heartbeatInterval().toNanos()) {
                return;
            }
            pingSentAtNanos = now;
            socket.write(Buffer.buffer(
                            RpcProtocolCodec.encodeHeartbeat(
                                    RpcMessageType.PING)))
                    .onFailure(this::failAll);
        }

        private void handleHeartbeat(byte[] bytes) {
            try {
                if (!negotiated.features().contains(RpcFeature.HEARTBEAT)) {
                    throw new RpcProtocolException(
                            "RPC heartbeat was not negotiated");
                }
                RpcFrame frame = RpcProtocolCodec.decode(bytes);
                if (frame.requestId() != 0L
                        || frame.codec() != RpcCodecIds.CONTROL
                        || frame.status() != RpcStatus.OK
                        || frame.payload().length != 0) {
                    throw new RpcProtocolException(
                            "Invalid RPC heartbeat frame");
                }
                if (frame.messageType() == RpcMessageType.PING) {
                    socket.write(Buffer.buffer(
                                    RpcProtocolCodec.encodeHeartbeat(
                                            RpcMessageType.PONG)))
                            .onFailure(this::failAll);
                    return;
                }
                if (frame.messageType() != RpcMessageType.PONG) {
                    throw new RpcProtocolException(
                            "Unexpected RPC heartbeat frame");
                }
            } catch (Throwable error) {
                failAll(error);
            }
        }

        private void cancelHandshakeTimer() {
            if (handshakeTimerId >= 0L) {
                vertx.cancelTimer(handshakeTimerId);
                handshakeTimerId = -1L;
            }
        }

        private void cancelHeartbeatTimer() {
            if (heartbeatTimerId >= 0L) {
                vertx.cancelTimer(heartbeatTimerId);
                heartbeatTimerId = -1L;
            }
            pingSentAtNanos = 0L;
        }

        private void failAll(Throwable error) {
            RpcConnectionCloseReason reason =
                    !handshake.isDone()
                            || error instanceof RpcProtocolException
                    ? RpcConnectionCloseReason.PROTOCOL_ERROR
                    : RpcConnectionCloseReason.TRANSPORT_ERROR;
            failAll(error, reason);
        }

        private void failAll(
                Throwable error,
                RpcConnectionCloseReason reason) {
            if (Vertx.currentContext() != context) {
                context.runOnContext(
                        ignored -> failAll(error, reason));
                return;
            }
            if (!open) {
                return;
            }
            open = false;
            if (negotiated != null && handshake.isDone()
                    && !handshake.isCompletedExceptionally()) {
                group.failed(slot, this);
            } else {
                group.release(slot, this);
            }
            failPendingRequests(error);
            cancelHandshakeTimer();
            cancelHeartbeatTimer();
            if (!handshake.isDone()) {
                handshake.completeExceptionally(error);
            }
            observeClosed(reason, error);
            LOGGER.debug(
                    "RPC connection closed: {}",
                    endpoint.authority());
            socket.close();
        }

        private void closeGracefully(
                RpcConnectionCloseReason reason) {
            if (!open) {
                return;
            }
            open = false;
            group.release(slot, this);
            cancelHandshakeTimer();
            cancelHeartbeatTimer();
            observeClosed(reason, null);
        }

        private void closeLocally() {
            if (!open) {
                return;
            }
            open = false;
            group.release(slot, this);
            RpcUnavailableException error =
                    new RpcUnavailableException(
                            "RPC connection is closing: "
                                    + endpoint.authority());
            failPendingRequests(error);
            cancelHandshakeTimer();
            cancelHeartbeatTimer();
            if (!handshake.isDone()) {
                handshake.completeExceptionally(error);
            }
            observeClosed(
                    RpcConnectionCloseReason.LOCAL_CLOSE,
                    null);
            socket.close();
        }

        private void failPendingRequests(Throwable error) {
            pending.values().forEach(request -> {
                vertx.cancelTimer(request.timerId());
                request.future().completeExceptionally(error);
            });
            pending.clear();
            inflight = 0;
        }

        private void observeClosed(
                RpcConnectionCloseReason reason,
                Throwable error) {
            if (closeObserved || !options.observer().enabled()) {
                return;
            }
            closeObserved = true;
            options.observer().onConnectionClosed(
                    RpcConnectionRole.CLIENT,
                    endpoint,
                    reason,
                    error);
        }

        private void close() {
            if (Vertx.currentContext() == context) {
                closeLocally();
            } else {
                context.runOnContext(
                        ignored -> closeLocally());
            }
        }
    }

    private record PendingRequest(
            CompletableFuture<byte[]> future,
            long timerId) {
    }

    private void startTlsReload() {
        if (!options.security().enabled()) {
            return;
        }
        tlsFileState = VertxTlsSupport.fileState(
                options.security());
        tlsReloadTimerId = vertx.setPeriodic(
                options.security()
                        .reloadInterval()
                        .toMillis(),
                ignored -> reloadTlsIfChanged());
    }

    private void reloadTlsIfChanged() {
        if (closed.get()) {
            return;
        }
        VertxTlsSupport.FileState current =
                VertxTlsSupport.fileState(
                        options.security());
        if (current.equals(tlsFileState)) {
            return;
        }
        long startedAtNanos = System.nanoTime();
        try {
            var sslOptions = VertxTlsSupport.reloadOptions(
                    options.security(),
                    true,
                    options.observer());
            client.updateSSLOptions(
                            sslOptions,
                            true)
                    .onComplete(result -> {
                        Throwable error =
                                result.failed()
                                        ? result.cause()
                                        : null;
                        if (error == null) {
                            tlsFileState = current;
                        }
                        observeCertificateReload(
                                startedAtNanos,
                                error);
                    });
        } catch (RuntimeException error) {
            observeCertificateReload(
                    startedAtNanos,
                    error);
        }
    }

    private void observeCertificateReload(
            long startedAtNanos,
            Throwable error) {
        if (!options.observer().enabled()) {
            return;
        }
        options.observer()
                .onCertificateReloadCompleted(
                        options.security().mode(),
                        error == null
                                ? RpcCertificateReloadOutcome.SUCCESS
                                : RpcCertificateReloadOutcome.FAILURE,
                        System.nanoTime() - startedAtNanos,
                        error);
    }

    private void cancelTlsReload() {
        if (tlsReloadTimerId >= 0L) {
            vertx.cancelTimer(tlsReloadTimerId);
            tlsReloadTimerId = -1L;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelTlsReload();
        groups.values().forEach(ConnectionGroup::close);
        groups.clear();
        client.close();
        vertx.close();
    }
}
