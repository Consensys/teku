/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.networking.p2p.libp2p.rpc;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.multistream.Mode;
import io.libp2p.core.multistream.Multistream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.artemis.networking.p2p.rpc.StreamTimeoutException;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;

public class RpcHandler implements ProtocolBinding<Controller> {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Logger LOG = LogManager.getLogger();

  private final RpcMethod rpcMethod;
  private final AsyncRunner asyncRunner;

  public RpcHandler(final AsyncRunner asyncRunner, RpcMethod rpcMethod) {
    this.asyncRunner = asyncRunner;
    this.rpcMethod = rpcMethod;
  }

  @SuppressWarnings("unchecked")
  public SafeFuture<RpcStream> sendRequest(
      Connection connection, Bytes initialPayload, RpcRequestHandler handler) {
    if (connection.closeFuture().isDone()) {
      return SafeFuture.failedFuture(new PeerDisconnectedException());
    }

    SafeFuture<RpcStream> streamFuture = new SafeFuture<>();

    // Complete future if peer disconnects
    SafeFuture.of(connection.closeFuture())
        .always(() -> streamFuture.completeExceptionally(new PeerDisconnectedException()));
    // Complete future if we fail to initialize
    asyncRunner
        .getDelayedFuture(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .thenAccept(
            __ ->
                streamFuture.completeExceptionally(
                    new StreamTimeoutException("Timed out waiting to initialize stream")))
        .reportExceptions();

    // Try to initiate stream
    SafeFuture.of(
            connection
                .muxerSession()
                .createStream(
                    Multistream.create(this.toInitiator(rpcMethod.getId())).toStreamHandler())
                .getController())
        .thenCompose(
            ctr -> {
              ctr.setRequestHandler(handler);
              return ctr.getRpcStream()
                  .writeBytes(initialPayload)
                  .thenApply(f -> ctr.getRpcStream())
                  .thenAccept(
                      rpcStream -> {
                        if (!streamFuture.complete(rpcStream)) {
                          // If future was already completed exceptionally, close the controller
                          ctr.close();
                        }
                      });
            })
        .exceptionally(
            err -> {
              streamFuture.completeExceptionally(err);
              return null;
            })
        .reportExceptions();

    return streamFuture;
  }

  @NotNull
  @Override
  public String getAnnounce() {
    return rpcMethod.getId();
  }

  @NotNull
  @Override
  public ProtocolMatcher getMatcher() {
    return new ProtocolMatcher(Mode.STRICT, getAnnounce(), null);
  }

  @NotNull
  @Override
  public SafeFuture<Controller> initChannel(P2PChannel channel, String s) {
    final Connection connection = ((io.libp2p.core.Stream) channel).getConnection();
    final NodeId nodeId = new LibP2PNodeId(connection.secureSession().getRemoteId());
    Controller controller = new Controller(nodeId, channel, asyncRunner);
    if (!channel.isInitiator()) {
      controller.setRequestHandler(rpcMethod.createIncomingRequestHandler());
    }
    channel.pushHandler(controller);
    return controller.activeFuture;
  }

  static class Controller extends SimpleChannelInboundHandler<ByteBuf> {
    private final NodeId nodeId;
    private final P2PChannel p2pChannel;
    private final AsyncRunner asyncRunner;
    private RpcRequestHandler rpcRequestHandler;
    private RpcStream rpcStream;

    private final PipedOutputStream outputStream;
    private final InputStream inputStream;
    private boolean outputStreamClosed = false;

    protected final SafeFuture<Controller> activeFuture = new SafeFuture<>();

    private Controller(
        final NodeId nodeId, final P2PChannel p2pChannel, final AsyncRunner asyncRunner) {
      this.nodeId = nodeId;
      this.p2pChannel = p2pChannel;
      this.asyncRunner = asyncRunner;

      try {
        outputStream = new PipedOutputStream();
        inputStream = new PipedInputStream(outputStream, 2048);
      } catch (IOException e) {
        // We should never hits this
        // This exception should only be thrown if input and output are incorrectly connected
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      rpcStream = new LibP2PRpcStream(nodeId, p2pChannel, ctx);
      activeFuture.complete(this);
    }

    public RpcStream getRpcStream() {
      return rpcStream;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf msg) {
      if (outputStreamClosed) {
        // Discard any data if output stream has been closed
        return;
      }
      try {
        // TODO - we may want to optimize this to pass on ByteBuf's directly and manage their
        //  garbage collection rather than immediately copying these bytes
        final Bytes bytes = Bytes.wrapByteBuf(msg);
        outputStream.write(bytes.toArray());
      } catch (IOException e) {
        // We should only hit this if the connected input pipe has been prematurely closed
        throw new IllegalStateException(e);
      }
    }

    public void setRequestHandler(RpcRequestHandler rpcRequestHandler) {
      if (this.rpcRequestHandler != null) {
        throw new IllegalStateException("Attempt to set an already set data handler");
      }
      this.rpcRequestHandler = rpcRequestHandler;

      activeFuture
          .thenCompose(
              __ ->
                  asyncRunner.runAsync(
                      () -> rpcRequestHandler.processInput(nodeId, rpcStream, inputStream)))
          .handle(
              (res, err) -> {
                if (err != null) {
                  LOG.error("Unhandled exception while processing rpc input", err);
                }
                try {
                  inputStream.close();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                return null;
              })
          .reportExceptions();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      LOG.error("Unhandled error while processes req/response", cause);
      final IllegalStateException exception = new IllegalStateException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws IllegalArgumentException {
      close();
    }

    private void close() {
      if (rpcStream != null) {
        rpcStream.close().reportExceptions();
      }
      closeOutputStream();
      // Make sure to complete activation future in case we are never activated
      activeFuture.completeExceptionally(new StreamClosedException());
    }

    private void closeOutputStream() {
      if (!outputStreamClosed) {
        outputStreamClosed = true;
        try {
          outputStream.close();
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
    }
  }
}
