/*
 * Copyright 2020 ConsenSys AG.
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.util.async.SafeFuture;

public class LibP2PRpcStream implements RpcStream {
  private ChannelHandlerContext ctx;
  private AtomicBoolean closed = new AtomicBoolean(false);

  public LibP2PRpcStream(final ChannelHandlerContext ctx) {
    this.ctx = ctx;
  }

  @Override
  public SafeFuture<Void> writeBytes(final Bytes bytes) throws StreamClosedException {
    if (closed.get()) {
      throw new StreamClosedException();
    }
    final ByteBuf reqByteBuf = ctx.alloc().buffer();
    reqByteBuf.writeBytes(bytes.toArrayUnsafe());

    return toSafeFuture(ctx.writeAndFlush(reqByteBuf));
  }

  @Override
  public SafeFuture<Void> closeStream() {
    closed.set(true);
    return toSafeFuture(ctx.channel().close());
  }

  @Override
  public SafeFuture<Void> disconnect() {
    closed.set(true);
    return toSafeFuture(ctx.channel().disconnect());
  }

  private SafeFuture<Void> toSafeFuture(ChannelFuture channelFuture) {
    final SafeFuture<Void> future = new SafeFuture<>();
    channelFuture.addListener(
        (f) -> {
          if (f.isSuccess()) {
            future.complete(null);
          } else {
            future.completeExceptionally(f.cause());
          }
        });
    return future;
  }
}
