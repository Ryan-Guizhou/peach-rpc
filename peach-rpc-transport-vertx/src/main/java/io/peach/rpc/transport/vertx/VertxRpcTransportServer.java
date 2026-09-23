package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcRemoteError;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcErrorCodec;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcHandshakeCodec;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcNegotiatedCapabilities;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.peach.rpc.transport.RpcRequestHandler;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.transport.RpcTransportServer;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Vert.x TCP Provider 传输服务器。 */
final class VertxRpcTransportServer implements RpcTransportServer {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(VertxRpcTransportServer.class);
    private static final int MESSAGE_TYPE_OFFSET = 6;
    private static final int CODEC_OFFSET = 7;

    private final Vertx vertx = Vertx.vertx();
    private final NetServer server;
    private final RpcTransportOptions options;

    VertxRpcTransportServer(RpcTransportOptions options) {
        this.options = options;
        this.server = vertx.createNetServer(
                new NetServerOptions()
                        .setTcpKeepAlive(true)
                        .setTcpNoDelay(true));
    }

    @Override
    public CompletionStage<RpcEndpoint> start(
            RpcEndpoint bind,
            RpcRequestHandler handler) {
        CompletableFuture<RpcEndpoint> started =
                new CompletableFuture<>();
        server.connectHandler(socket ->
                new ServerConnection(socket, handler))
                .listen(bind.port(), bind.host())
                .onComplete(result -> {
                    if (result.failed()) {
                        started.completeExceptionally(result.cause());
                    } else {
                        started.complete(new RpcEndpoint(
                                bind.host(),
                                result.result().actualPort()));
                    }
                });
        return started;
    }

    private final class ServerConnection {
        private final NetSocket socket;
        private final RpcRequestHandler handler;
        private final Context context;
        private final RpcEndpoint remote;
        private final FrameAccumulator accumulator =
                new FrameAccumulator(options.maxFrameBytes());

        private boolean open = true;
        private boolean handshakeComplete;
        private long handshakeTimerId = -1L;
        private RpcNegotiatedCapabilities negotiated;

        private ServerConnection(
                NetSocket socket,
                RpcRequestHandler handler) {
            this.socket = socket;
            this.handler = handler;
            this.context = Vertx.currentContext();
            if (context == null) {
                throw new IllegalStateException(
                        "Vert.x server connection must use an event loop");
            }
            this.remote = new RpcEndpoint(
                    socket.remoteAddress().host(),
                    Math.max(
                            socket.remoteAddress().port(),
                            1));

            socket.setWriteQueueMaxSize(
                    options.maxWriteQueueBytes());
            socket.handler(this::onBuffer);
            socket.exceptionHandler(this::closeWithError);
            socket.closeHandler(ignored -> {
                cancelHandshakeTimer();
                open = false;
            });
            handshakeTimerId = vertx.setTimer(
                    options.handshakeTimeout().toMillis(),
                    ignored -> closeMalformed(new RpcProtocolException(
                            "RPC handshake timed out")));
        }

        private void onBuffer(Buffer buffer) {
            try {
                accumulator.accept(buffer, this::onFrame);
            } catch (RuntimeException error) {
                closeMalformed(error);
            }
        }

        private void onFrame(byte[] frame) {
            if (!handshakeComplete) {
                handleHello(frame);
                return;
            }
            if (frame.length > negotiated.maxFrameBytes()) {
                closeMalformed(new RpcProtocolException(
                        "RPC frame exceeds negotiated maxFrameBytes"));
                return;
            }
            if (frame[MESSAGE_TYPE_OFFSET]
                    != RpcMessageType.REQUEST.code()) {
                closeMalformed(new RpcProtocolException(
                        "Expected REQUEST after handshake"));
                return;
            }
            byte codecId = frame[CODEC_OFFSET];
            if (!negotiated.codecIds().contains(codecId)) {
                closeMalformed(new RpcProtocolException(
                        "RPC codec was not negotiated: "
                                + Byte.toUnsignedInt(codecId)));
                return;
            }

            handler.handle(remote, frame)
                    .whenComplete((response, error) ->
                            context.runOnContext(ignored ->
                                    completeRequest(
                                            response,
                                            error)));
        }

        private void handleHello(byte[] bytes) {
            try {
                RpcFrame hello = RpcProtocolCodec.decode(bytes);
                if (hello.messageType() != RpcMessageType.HELLO
                        || hello.requestId() != 0L
                        || hello.codec() != RpcCodecIds.CONTROL) {
                    throw new RpcProtocolException(
                            "Expected HELLO as first client frame");
                }
                var clientCapabilities =
                        RpcHandshakeCodec.decode(hello.payload());
                negotiated = RpcHandshakeCodec.negotiate(
                        clientCapabilities,
                        options.capabilities());

                RpcFrame ack = new RpcFrame(
                        RpcMessageType.HELLO_ACK,
                        RpcCodecIds.CONTROL,
                        RpcStatus.OK,
                        0L,
                        0,
                        0,
                        Map.of(),
                        RpcHandshakeCodec.encode(
                                options.capabilities()));
                byte[] encoded = RpcProtocolCodec.encode(ack);
                handshakeComplete = true;
                cancelHandshakeTimer();
                socket.write(Buffer.buffer(encoded))
                        .onFailure(this::closeWithError);
            } catch (Throwable error) {
                closeMalformed(error);
            }
        }

        private void completeRequest(
                byte[] response,
                Throwable error) {
            if (!open) {
                return;
            }
            if (error != null) {
                LOGGER.error(
                        "RPC request handler failed for remote={}",
                        remote.authority(),
                        error);
                goAwayAndClose(
                        RpcStatus.INTERNAL_ERROR,
                        "RPC request handler failed: "
                                + error.getClass().getName());
                return;
            }
            if (response.length > negotiated.maxFrameBytes()) {
                LOGGER.warn(
                        "Closing RPC connection because response frame "
                                + "exceeds negotiated max size: {}",
                        remote.authority());
                goAwayAndClose(
                        RpcStatus.OVERLOADED,
                        "RPC response exceeds negotiated max frame size");
                return;
            }
            if (socket.writeQueueFull()) {
                LOGGER.warn(
                        "Closing RPC connection because write queue is full: {}",
                        remote.authority());
                goAwayAndClose(
                        RpcStatus.OVERLOADED,
                        "RPC response write queue is full");
                return;
            }
            socket.write(Buffer.buffer(response))
                    .onFailure(this::closeWithError);
        }

        private void cancelHandshakeTimer() {
            if (handshakeTimerId >= 0L) {
                vertx.cancelTimer(handshakeTimerId);
                handshakeTimerId = -1L;
            }
        }

        private void closeMalformed(Throwable error) {
            LOGGER.warn(
                    "Closing malformed RPC connection from {}",
                    remote.authority(),
                    error);
            if (!open) {
                return;
            }
            goAwayAndClose(
                    RpcStatus.BAD_REQUEST,
                    error.getMessage() == null
                            ? "RPC protocol rejected"
                            : error.getMessage());
        }

        private void goAwayAndClose(
                RpcStatus status,
                String message) {
            if (!open) {
                return;
            }
            cancelHandshakeTimer();
            RpcFrame goAway = new RpcFrame(
                    RpcMessageType.GO_AWAY,
                    RpcCodecIds.CONTROL,
                    status,
                    0L,
                    0,
                    0,
                    Map.of(),
                    RpcErrorCodec.encode(new RpcRemoteError(
                            RpcProtocolException.class.getName(),
                            message)));
            socket.end(Buffer.buffer(
                    RpcProtocolCodec.encode(goAway)));
        }

        private void closeWithError(Throwable error) {
            if (!open) {
                return;
            }
            cancelHandshakeTimer();
            LOGGER.debug(
                    "RPC connection failed for remote={}",
                    remote.authority(),
                    error);
            socket.close();
        }
    }

    @Override
    public void close() {
        server.close();
        vertx.close();
    }
}
