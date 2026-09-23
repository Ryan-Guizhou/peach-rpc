package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.transport.RpcRequestHandler;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.transport.RpcTransportServer;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Vert.x TCP Provider 传输服务器。 */
final class VertxRpcTransportServer implements RpcTransportServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxRpcTransportServer.class);

    private final Vertx vertx = Vertx.vertx();
    private final NetServer server;
    private final RpcTransportOptions options;

    VertxRpcTransportServer(RpcTransportOptions options) {
        this.options = options;
        this.server = vertx.createNetServer(new NetServerOptions().setTcpKeepAlive(true));
    }

    @Override
    public CompletionStage<RpcEndpoint> start(RpcEndpoint bind, RpcRequestHandler handler) {
        CompletableFuture<RpcEndpoint> started = new CompletableFuture<>();
        server.connectHandler(socket -> {
            socket.setWriteQueueMaxSize(options.maxWriteQueueBytes());
            FrameAccumulator accumulator = new FrameAccumulator(options.maxFrameBytes());
            RpcEndpoint remote = new RpcEndpoint(
                    socket.remoteAddress().host(), Math.max(socket.remoteAddress().port(), 1));

            socket.handler(buffer -> {
                try {
                    accumulator.accept(buffer, frame -> handler.handle(remote, frame)
                            .whenComplete((response, error) -> {
                                if (error != null) {
                                    LOGGER.error(
                                            "RPC request handler failed for remote={}",
                                            remote.authority(),
                                            error);
                                    socket.close();
                                } else if (socket.writeQueueFull()) {
                                    LOGGER.warn(
                                            "Closing RPC connection because write queue is full: {}",
                                            remote.authority());
                                    socket.close();
                                } else if (response.length > options.maxFrameBytes()) {
                                    LOGGER.warn(
                                            "Closing RPC connection because response frame is too large: {}",
                                            remote.authority());
                                    socket.close();
                                } else {
                                    socket.write(Buffer.buffer(response)).onFailure(writeError -> {
                                        LOGGER.debug(
                                                "RPC response write failed for remote={}",
                                                remote.authority(),
                                                writeError);
                                        socket.close();
                                    });
                                }
                            }));
                } catch (RuntimeException error) {
                    LOGGER.warn(
                            "Closing malformed RPC connection from {}",
                            remote.authority(),
                            error);
                    socket.close();
                }
            });
        }).listen(bind.port(), bind.host()).onComplete(result -> {
            if (result.failed()) {
                started.completeExceptionally(result.cause());
            } else {
                started.complete(new RpcEndpoint(bind.host(), result.result().actualPort()));
            }
        });
        return started;
    }

    @Override
    public void close() {
        server.close();
        vertx.close();
    }
}
