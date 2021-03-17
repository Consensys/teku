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

import com.google.common.annotations.VisibleForTesting;
import io.libp2p.core.ChannelVisitor;
import io.libp2p.core.Connection;
import io.libp2p.etc.util.netty.mux.MuxId;
import io.libp2p.mux.MuxFrame;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.FutureUtil;

public class MplexFirewall implements ChannelVisitor<Connection> {

  private static final Logger LOG = LogManager.getLogger();
  private static final long ONE_SECOND = 1000;

  private final int remoteOpenStreamsRateLimit;
  private final int remoteParallelOpenStreamsLimit;
  private final Supplier<Long> currentTimeSupplier;

  public MplexFirewall(int remoteOpenStreamsRateLimit, int remoteParallelOpenStreamsLimit) {
    this(remoteOpenStreamsRateLimit, remoteParallelOpenStreamsLimit, System::currentTimeMillis);
  }

  @VisibleForTesting
  MplexFirewall(
      int remoteOpenStreamsRateLimit,
      int remoteParallelOpenStreamsLimit,
      Supplier<Long> currentTimeSupplier) {
    this.remoteOpenStreamsRateLimit = remoteOpenStreamsRateLimit;
    this.remoteParallelOpenStreamsLimit = remoteParallelOpenStreamsLimit;
    this.currentTimeSupplier = currentTimeSupplier;
  }

  protected void remoteParallelOpenStreamLimitExceeded(MplexFirewallHandler peerMplexHandler) {
    LOG.debug("Abruptly closing peer connection due to exceeding parallel open streams limit");
    FutureUtil.ignoreFuture(peerMplexHandler.getConnection().close());
  }

  protected void remoteOpenFrameRateLimitExceeded(MplexFirewallHandler peerMplexHandler) {
    LOG.debug("Abruptly closing peer connection due to exceeding open mplex frame rate limit");
    FutureUtil.ignoreFuture(peerMplexHandler.getConnection().close());
  }

  @Override
  public void visit(@NotNull Connection connection) {
    MplexFirewallHandler firewallHandler = new MplexFirewallHandler(connection);
    connection.pushHandler(firewallHandler);
  }

  private class MplexFirewallHandler extends ChannelDuplexHandler {

    private final Connection connection;
    private int openFrameCounter = 0;
    private long startCounterTime = 0;
    private final Set<MuxId> remoteOpenedStreamIds = new HashSet<>();

    public MplexFirewallHandler(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      MuxFrame muxFrame = (MuxFrame) msg;
      boolean blockFrame = false;
      if (muxFrame.getFlag() == MuxFrame.Flag.OPEN) {
        remoteOpenedStreamIds.add(muxFrame.getId());
        if (remoteOpenedStreamIds.size() > remoteParallelOpenStreamsLimit) {
          remoteParallelOpenStreamLimitExceeded(this);
          blockFrame = true;
        }

        long curTime = currentTimeSupplier.get();
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
      } else if (muxFrame.getFlag() == MuxFrame.Flag.CLOSE
          || muxFrame.getFlag() == MuxFrame.Flag.RESET) {
        remoteOpenedStreamIds.remove(muxFrame.getId());
      }
      if (!blockFrame) {
        ctx.fireChannelRead(msg);
      }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
      MuxFrame muxFrame = (MuxFrame) msg;
      if (muxFrame.getFlag() == MuxFrame.Flag.RESET) {
        // Track only RESET since CLOSE from local doesn't close the stream for writing from remote
        remoteOpenedStreamIds.remove(muxFrame.getId());
      }
      // ignoring since the write() just returns `promise` instance
      FutureUtil.ignoreFuture(ctx.write(msg, promise));
    }

    public Connection getConnection() {
      return connection;
    }
  }
}
