/*
 * Copyright 2021 ConsenSys AG.
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

import io.libp2p.core.ChannelVisitor;
import io.libp2p.core.Connection;
import io.libp2p.mux.MuxFrame;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class MplexFirewall implements ChannelVisitor<Connection> {
  private static final Logger LOG = LogManager.getLogger();
  private static final long ONE_SECOND = 1000;

  private class MplexFirewallHandler extends ChannelDuplexHandler {
    private final Connection connection;
    private int openFrameCounter = 0;
    private long startCounterTime = 0;

    public MplexFirewallHandler(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      MuxFrame muxFrame = (MuxFrame) msg;
      boolean blockFrame = false;
      if (muxFrame.getFlag() == MuxFrame.Flag.OPEN) {
        long curTime = System.currentTimeMillis();
        if (curTime - startCounterTime > ONE_SECOND) {
          startCounterTime = curTime;
          openFrameCounter = 0;
        } else {
          openFrameCounter++;
          if (openFrameCounter > remoteOpenStreamsRateLimit) {
            remoteOpenFrameRateLimitExceeded(this);
            blockFrame = true;
          }
        }
      }
      if (!blockFrame) {
        ctx.fireChannelRead(msg);
      }
    }

    public Connection getConnection() {
      return connection;
    }
  }

  private final int remoteOpenStreamsRateLimit;

  public MplexFirewall(int remoteOpenStreamsRateLimit) {
    this.remoteOpenStreamsRateLimit = remoteOpenStreamsRateLimit;
  }

  protected void remoteOpenFrameRateLimitExceeded(MplexFirewallHandler peerMplexHandler) {
    LOG.debug("Abruptly closing peer connection due to exceeding open mplex frame rate limit");
    peerMplexHandler.getConnection().close();
  }

  @Override
  public void visit(@NotNull Connection connection) {
    MplexFirewallHandler firewallHandler = new MplexFirewallHandler(connection);
    connection.pushHandler(firewallHandler);
  }
}
