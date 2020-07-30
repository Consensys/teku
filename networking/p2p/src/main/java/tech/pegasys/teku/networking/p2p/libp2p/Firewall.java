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

package tech.pegasys.teku.networking.p2p.libp2p;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.FutureUtil;

/**
 * The very first Netty handler in the Libp2p connection pipeline. Sets up Netty Channel options and
 * doing other duties preventing DoS attacks
 */
@Sharable
public class Firewall extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final Duration writeTimeout;
  private final List<ChannelHandler> additionalHandlers;

  public Firewall(Duration writeTimeout, List<ChannelHandler> additionalHandlers) {
    this.writeTimeout = writeTimeout;
    this.additionalHandlers = additionalHandlers;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    additionalHandlers.forEach(h -> ctx.pipeline().addLast(h));
    ctx.channel().config().setWriteBufferWaterMark(new WriteBufferWaterMark(100, 1024));
    ctx.pipeline().addLast(new WriteTimeoutHandler(writeTimeout.toMillis(), TimeUnit.MILLISECONDS));
    ctx.pipeline().addLast(new FirewallExceptionHandler());
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) {
    ctx.channel().config().setAutoRead(ctx.channel().isWritable());
    ctx.fireChannelWritabilityChanged();
  }

  class FirewallExceptionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      if (cause instanceof WriteTimeoutException) {
        LOG.debug("Firewall closed channel by write timeout. No writes during " + writeTimeout);
      } else {
        LOG.debug("Error in Firewall, disconnecting" + cause);
        FutureUtil.ignoreFuture(ctx.close());
      }
    }
  }
}
