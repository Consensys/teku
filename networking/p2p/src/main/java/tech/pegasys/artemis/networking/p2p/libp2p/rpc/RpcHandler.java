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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    connection
        .muxerSession()
        .createStream(Multistream.create(this.toInitiator(rpcMethod.getId())).toStreamHandler())
        .getController()
        .thenAccept(
            ctr -> {
              ctr.setRequestHandler(handler);
              ctr.getRpcStream()
                  .writeBytes(initialPayload)
                  .thenApply(f -> ctr.getRpcStream())
                  .thenAccept(
                      rpcStream -> {
                        if (!streamFuture.complete(rpcStream)) {
                          // If future was already completed exceptionally, close down the
                          // controller
                          ctr.close();
                        }
                      })
                  .exceptionally(
                      err -> {
                        streamFuture.completeExceptionally(err);
                        return null;
                      })
                  .reportExceptions();
            })
        .exceptionally(
            err -> {
              streamFuture.completeExceptionally(err);
              return null;
            });

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
    Controller controller = new Controller(nodeId, channel);
    if (!channel.isInitiator()) {
      controller.setRequestHandler(rpcMethod.createIncomingRequestHandler());
    }
    channel.pushHandler(controller);

    return controller.activeFuture;
  }

  static class Controller extends SimpleChannelInboundHandler<ByteBuf> {
    private final NodeId nodeId;
    private final P2PChannel p2pChannel;
    private RpcRequestHandler rpcRequestHandler;
    private RpcStream rpcStream;
    private List<ByteBuf> bufferedData = new ArrayList<>();

    protected final SafeFuture<Controller> activeFuture = new SafeFuture<>();

    private Controller(final NodeId nodeId, final P2PChannel p2pChannel) {
      this.nodeId = nodeId;
      this.p2pChannel = p2pChannel;
      SafeFuture.of(this.p2pChannel.closeFuture())
          .always(() -> activeFuture.completeExceptionally(new StreamClosedException()));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      rpcStream = new LibP2PRpcStream(p2pChannel, ctx);
      activeFuture.complete(this);
    }

    public RpcStream getRpcStream() {
      return rpcStream;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf msg) {
      if (rpcRequestHandler != null) {
        rpcRequestHandler.onData(nodeId, rpcStream, msg);
      } else {
        bufferedData.add(msg);
      }
    }

    public void setRequestHandler(RpcRequestHandler rpcRequestHandler) {
      if (this.rpcRequestHandler != null) {
        throw new IllegalStateException("Attempt to set an already set data handler");
      }
      this.rpcRequestHandler = rpcRequestHandler;
      activeFuture.thenAccept(__ -> rpcRequestHandler.onActivation(rpcStream)).reportExceptions();
      while (!bufferedData.isEmpty()) {
        ByteBuf currentBuffer = bufferedData.remove(0);
        this.rpcRequestHandler.onData(nodeId, rpcStream, currentBuffer);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      LOG.error("Unhandled error while processes req/response", cause);
      final IllegalStateException exception = new IllegalStateException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      close();
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void handlerRemoved(ChannelHandlerContext ctx) throws IllegalArgumentException {
      close();
    }

    private void close() {
      if (rpcStream != null) {
        rpcStream.close().reportExceptions();
      }
      if (rpcRequestHandler != null) {
        rpcRequestHandler.onRequestComplete();
      }
      // Make sure to complete activation future in case we are never activated
      activeFuture.completeExceptionally(new StreamClosedException());
    }
  }
}
