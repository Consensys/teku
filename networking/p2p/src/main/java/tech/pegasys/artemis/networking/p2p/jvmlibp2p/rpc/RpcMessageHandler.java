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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PAbstractChannel;
import io.libp2p.core.multistream.Mode;
import io.libp2p.core.multistream.Multistream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.PeerLookup;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMessageHandler.Controller;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class RpcMessageHandler<
        TRequest extends RpcRequest, TResponse extends SimpleOffsetSerializable>
    implements ProtocolBinding<Controller<TRequest, ResponseStream<TResponse>>> {
  private static final Logger LOG = LogManager.getLogger();

  private final RpcMethod<TRequest, TResponse> method;
  private final PeerLookup peerLookup;
  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final RpcEncoder rpcEncoder;
  private boolean closeNotification = false;

  public RpcMessageHandler(
      RpcMethod<TRequest, TResponse> method,
      PeerLookup peerLookup,
      LocalMessageHandler<TRequest, TResponse> localMessageHandler) {
    this.method = method;
    this.peerLookup = peerLookup;
    this.localMessageHandler = localMessageHandler;
    this.rpcEncoder = new RpcEncoder(method.getEncoding());
  }

  @SuppressWarnings("unchecked")
  public CompletableFuture<ResponseStream<TResponse>> invokeRemote(
      Connection connection, TRequest request) {
    return connection
        .getMuxerSession()
        .createStream(
            Multistream.create(this.toInitiator(method.getMultistreamId())).toStreamHandler())
        .getControler()
        .thenCompose(ctr -> ctr.invoke(request));
  }

  private void invokeLocal(
      Connection connection, TRequest request, ResponseCallback<TResponse> callback) {
    try {
      final Peer peer = peerLookup.getPeer(connection);
      localMessageHandler.onIncomingMessage(peer, request, callback);
    } catch (final Throwable t) {
      LOG.error("Unhandled error while processing request " + method.getMultistreamId(), t);
      callback.completeWithError(RpcException.SERVER_ERROR);
    }
  }

  public RpcMessageHandler<TRequest, TResponse> setCloseNotification() {
    this.closeNotification = true;
    return this;
  }

  @NotNull
  @Override
  public String getAnnounce() {
    return method.getMultistreamId();
  }

  public RpcMethod<TRequest, TResponse> getMethod() {
    return method;
  }

  @NotNull
  @Override
  public ProtocolMatcher getMatcher() {
    return new ProtocolMatcher(Mode.STRICT, getAnnounce(), null);
  }

  @NotNull
  @Override
  public CompletableFuture<AbstractHandler> initChannel(P2PAbstractChannel channel, String s) {
    // TODO timeout handlers
    AbstractHandler handler;
    if (channel.isInitiator()) {
      handler = new RequesterHandler();
    } else {
      handler = new ResponderHandler(((io.libp2p.core.Stream) channel).getConn());
    }
    channel.getNettyChannel().pipeline().addLast(handler);
    return handler.activeFuture;
  }

  interface Controller<TRequest, TResponse> {
    CompletableFuture<TResponse> invoke(TRequest request);
  }

  private abstract class AbstractHandler extends SimpleChannelInboundHandler<ByteBuf>
      implements Controller<TRequest, ResponseStream<TResponse>> {

    protected final CompletableFuture<AbstractHandler> activeFuture = new CompletableFuture<>();
  }

  private class ResponderHandler extends AbstractHandler {
    private final Connection connection;
    private RequestRpcDecoder<TRequest> requestReader = new RequestRpcDecoder<>(method);
    private ResponseCallback<TResponse> callback;

    public ResponderHandler(Connection connection) {
      this.connection = connection;
      activeFuture.complete(this);
    }

    @Override
    public CompletableFuture<ResponseStream<TResponse>> invoke(TRequest tRequest) {
      throw new IllegalStateException("This method shouldn't be called for Responder");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
      STDOUT.log(Level.DEBUG, "Responder received " + byteBuf.array().length + " bytes.");
      if (callback == null) {
        callback = new RpcResponseCallback<>(ctx, rpcEncoder, closeNotification, connection);
      }
      try {
        requestReader
            .onDataReceived(byteBuf)
            .ifPresent(request -> invokeLocal(connection, request, callback));
      } catch (final RpcException e) {
        callback.completeWithError(e);
      }
    }
  }

  private class RequesterHandler extends AbstractHandler {
    private ChannelHandlerContext ctx;
    private int maximumResponseChunks;
    private ResponseStreamImpl<TResponse> responseStream;
    private ResponseRpcDecoder<TResponse> responseHandler;

    @Override
    public CompletableFuture<ResponseStream<TResponse>> invoke(TRequest request) {
      maximumResponseChunks = request.getMaximumRequestChunks();
      final ByteBuf reqByteBuf = ctx.alloc().buffer();
      final Bytes encoded = rpcEncoder.encodeRequest(request);
      reqByteBuf.writeBytes(encoded.toArrayUnsafe());
      responseStream = new ResponseStreamImpl<>();
      responseHandler = new ResponseRpcDecoder<>(responseStream::respond, method);
      final ChannelFuture writeFuture = ctx.writeAndFlush(reqByteBuf);
      if (closeNotification) {
        writeFuture.addListener(
            future -> {
              ctx.channel().close();
              responseStream.completeSuccessfully();
            });
      }
      return CompletableFuture.completedFuture(responseStream);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf)
        throws IllegalArgumentException {
      if (responseHandler == null) {
        STDOUT.log(
            Level.WARN,
            "Received " + byteBuf.array().length + " bytes of data before requesting it.");
        throw new IllegalArgumentException("Some data received prior to request: " + byteBuf);
      }
      try {
        STDOUT.log(Level.TRACE, "Requester received " + byteBuf.capacity() + " bytes.");
        responseHandler.onDataReceived(byteBuf);
        if (responseStream.getResponseChunkCount() == maximumResponseChunks) {
          responseHandler.close();
          responseStream.completeSuccessfully();
          ctx.channel().disconnect();
        }
      } catch (final RpcException e) {
        LOG.debug("Request returned an error {}", e.getErrorMessage());
        responseStream.completeWithError(e);
      } catch (final Throwable t) {
        LOG.error("Failed to handle response", t);
        responseStream.completeWithError(t);
      }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      this.ctx = ctx;
      activeFuture.complete(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      LOG.error("Unhandled error while processes req/response", cause);
      final IllegalStateException exception = new IllegalStateException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      responseStream.completeWithError(exception);
      ctx.channel().close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws IllegalArgumentException {
      final IllegalStateException exception = new IllegalStateException("Stream closed.");
      // This is an error if we haven't already sent the request...
      activeFuture.completeExceptionally(exception);
      try {
        // But it just means the end of stream if we have.
        responseHandler.close();
        responseStream.completeSuccessfully();
      } catch (final RpcException e) {
        LOG.debug("Request returned an error {}", e.getErrorMessage());
        responseStream.completeWithError(e);
      } catch (final Throwable t) {
        LOG.error("Failed to handle response", t);
        responseStream.completeWithError(t);
      }
      ctx.channel().close();
    }
  }
}
