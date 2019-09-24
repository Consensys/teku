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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.network.NetworkImpl;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.util.alogger.ALogger;

public class Libp2pPeerManager implements ConnectionHandler {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final ALogger LOG = new ALogger(Libp2pPeerManager.class.getName());

  private final ScheduledExecutorService scheduler;
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(1);

  public Libp2pPeerManager(final ScheduledExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    LOG.log(Level.INFO, "Got new connection from ");
    final PeerId remoteId = connection.getSecureSession().getRemoteId();
    LOG.log(Level.INFO, "Got new connection from " + remoteId);
    connection.closeFuture().thenRun(() -> LOG.log(Level.INFO, "Peer disconnected: " + remoteId));
  }

  public CompletableFuture<?> connect(final Multiaddr peer, final NetworkImpl network) {
    STDOUT.log(Level.INFO, "Connecting to " + peer);
    return network
        .connect(peer)
        .whenComplete(
            (conn, t) -> {
              if (t != null) {
                STDOUT.log(
                    Level.INFO, "Connection to " + peer + " failed. Will retry shortly: " + t);
                scheduler.schedule(
                    () -> connect(peer, network),
                    RECONNECT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
              } else {
                conn.closeFuture()
                    .thenAccept(
                        ignore -> {
                          LOG.log(
                              Level.INFO, "Connection to " + peer + " closed. Will retry shortly");
                          scheduler.schedule(
                              () -> connect(peer, network),
                              RECONNECT_TIMEOUT.toMillis(),
                              TimeUnit.MILLISECONDS);
                        });
              }
            });
  }
}
