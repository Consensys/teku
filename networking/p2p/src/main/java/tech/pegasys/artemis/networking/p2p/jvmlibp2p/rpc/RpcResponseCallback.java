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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

class RpcResponseCallback<TResponse extends SimpleOffsetSerializable>
    implements ResponseCallback<TResponse> {
  private static final Logger LOG = LogManager.getLogger();
  private final ChannelHandlerContext ctx;
  private final RpcCodec rpcCodec;
  private final boolean closeNotification;
  private final Connection connection;

  public RpcResponseCallback(
      final ChannelHandlerContext ctx,
      final RpcCodec rpcCodec,
      final boolean closeNotification,
      final Connection connection) {
    this.ctx = ctx;
    this.rpcCodec = rpcCodec;
    this.closeNotification = closeNotification;
    this.connection = connection;
  }

  @Override
  public void respond(final TResponse data) {
    writeResponse(ctx, rpcCodec.encodeSuccessfulResponse(data));
  }

  @Override
  public void completeSuccessfully() {
    ctx.channel().disconnect();
    if (closeNotification) {
      connection.getNettyChannel().close();
    }
  }

  @Override
  public void completeWithError(final RpcException error) {
    LOG.debug("Responding to RPC request with error: {}", error.getErrorMessage());
    writeResponse(ctx, rpcCodec.encodeErrorResponse(error));
    ctx.channel().disconnect();
  }

  private void writeResponse(final ChannelHandlerContext ctx, final Bytes encoded) {
    ByteBuf respBuf = ctx.alloc().buffer();
    respBuf.writeBytes(encoded.toArrayUnsafe());
    ctx.writeAndFlush(respBuf);
  }
}
