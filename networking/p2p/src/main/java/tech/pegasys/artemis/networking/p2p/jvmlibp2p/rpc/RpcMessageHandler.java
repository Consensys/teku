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

import io.libp2p.core.Connection;
import io.libp2p.core.P2PAbstractChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.Mode;
import io.libp2p.core.multistream.Multistream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.PeerLookup;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMessageHandler.Controller;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class RpcMessageHandler<
        TRequest extends SimpleOffsetSerializable, TResponse extends SimpleOffsetSerializable>
    implements ProtocolBinding<Controller<TRequest, TResponse>> {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final RpcMethod<TRequest, TResponse> method;
  private final PeerLookup peerLookup;
  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final RpcCodec rpcCodec;
  private boolean closeNotification = false;

  public RpcMessageHandler(
      RpcMethod<TRequest, TResponse> method,
      PeerLookup peerLookup,
      LocalMessageHandler<TRequest, TResponse> localMessageHandler) {
    this.method = method;
    this.peerLookup = peerLookup;
    this.localMessageHandler = localMessageHandler;
    this.rpcCodec = new RpcCodec(method.getEncoding());
  }

  @SuppressWarnings("unchecked")
  public CompletableFuture<TResponse> invokeRemote(Connection connection, TRequest request) {
    return connection
        .getMuxerSession()
        .createStream(
            Multistream.create(this.toInitiator(method.getMultistreamId())).toStreamHandler())
        .getControler()
        .thenCompose(ctr -> ctr.invoke(request));
  }

  private CompletableFuture<TResponse> invokeLocal(Connection connection, TRequest request) {
    final Peer peer = peerLookup.getPeer(connection);
    return CompletableFuture.completedFuture(localMessageHandler.onIncomingMessage(peer, request));
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
      handler = new ResponderHandler(((Stream) channel).getConn());
    }
    channel.getNettyChannel().pipeline().addLast(handler);
    return handler.activeFuture;
  }

  interface Controller<TRequest, TResponse> {
    CompletableFuture<TResponse> invoke(TRequest request);
  }

  abstract class AbstractHandler extends SimpleChannelInboundHandler<ByteBuf>
      implements Controller<TRequest, TResponse> {

    final CompletableFuture<AbstractHandler> activeFuture = new CompletableFuture<>();
  }

  class ResponderHandler extends AbstractHandler {
    private final Connection connection;

    public ResponderHandler(Connection connection) {
      this.connection = connection;
      activeFuture.complete(this);
    }

    @Override
    public CompletableFuture<TResponse> invoke(TRequest tRequest) {
      throw new IllegalStateException("This method shouldn't be called for Responder");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
      STDOUT.log(Level.DEBUG, "Responder received " + byteBuf.array().length + " bytes.");
      Bytes bytes = Bytes.wrapByteBuf(byteBuf);
      TRequest request = rpcCodec.decodeRequest(bytes, method.getRequestType());

      invokeLocal(connection, request)
          .whenComplete(
              (response, err) -> {
                ByteBuf respBuf = ctx.alloc().buffer();
                final Bytes encoded = rpcCodec.encodeSuccessfulResponse(response);
                respBuf.writeBytes(encoded.toArrayUnsafe());
                ctx.writeAndFlush(respBuf).addListener(future -> ctx.channel().disconnect());
              });
    }
  }

  class RequesterHandler extends AbstractHandler {
    private ChannelHandlerContext ctx;
    private CompletableFuture<TResponse> respFuture;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf)
        throws IllegalArgumentException {
      if (respFuture == null) {
        STDOUT.log(
            Level.WARN,
            "Received " + byteBuf.array().length + " bytes of data before requesting it.");
        throw new IllegalArgumentException("Some data received prior to request: " + byteBuf);
      }
      try {
        STDOUT.log(Level.DEBUG, "Requester received " + byteBuf.array().length + " bytes.");
        Bytes bytes = Bytes.wrapByteBuf(byteBuf);
        final Response<TResponse> response =
            rpcCodec.decodeResponse(bytes, method.getResponseType());
        if (response.isSuccess()) {
          respFuture.complete(response.getData());
        } else {
          // TODO: Will have to handle this better when we have requests that may be rejected.
          respFuture.completeExceptionally(new IllegalStateException("Received failure response"));
        }
      } catch (final Throwable t) {
        respFuture.completeExceptionally(t);
      }
    }

    @Override
    public CompletableFuture<TResponse> invoke(TRequest request) {
      ByteBuf reqByteBuf = ctx.alloc().buffer();
      final Bytes encoded = rpcCodec.encodeRequest(request);
      reqByteBuf.writeBytes(encoded.toArrayUnsafe());
      respFuture = new CompletableFuture<>();
      ctx.writeAndFlush(reqByteBuf)
          .addListener(
              future -> {
                if (closeNotification) {
                  ctx.channel().close();
                  respFuture.complete(null);
                } else {
                  ctx.channel().disconnect();
                }
              });
      return respFuture;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      this.ctx = ctx;
      activeFuture.complete(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
        throws IllegalArgumentException {
      IllegalArgumentException exception = new IllegalArgumentException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      respFuture.completeExceptionally(exception);
      ctx.channel().close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws IllegalArgumentException {
      IllegalArgumentException exception = new IllegalArgumentException("Stream closed.");
      activeFuture.completeExceptionally(exception);
      respFuture.completeExceptionally(exception);
      ctx.channel().close();
    }
  }
}
