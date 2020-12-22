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

package tech.pegasys.teku.network.p2p;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.network.p2p.peer.SimplePeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.util.config.Constants;

public class DiscoveryNetworkFactory {

  protected static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 9000;
  private static final int MAX_PORT = 12000;

  private final List<DiscoveryNetwork<Peer>> networks = new ArrayList<>();

  public DiscoveryNetworkBuilder builder() {
    return new DiscoveryNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(DiscoveryNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class DiscoveryNetworkBuilder {
    private final List<String> staticPeers = new ArrayList<>();
    private final List<String> bootnodes = new ArrayList<>();

    private DiscoveryNetworkBuilder() {}

    public DiscoveryNetworkBuilder staticPeer(final String staticPeer) {
      this.staticPeers.add(staticPeer);
      return this;
    }

    public DiscoveryNetworkBuilder bootnode(final String bootnode) {
      this.bootnodes.add(bootnode);
      return this;
    }

    public DiscoveryNetwork<Peer> buildAndStart() throws Exception {
      int attempt = 1;
      while (true) {

        final Random random = new Random();
        final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);
        final NetworkConfig config =
            new NetworkConfig(
                KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1(),
                "127.0.0.1",
                Optional.empty(),
                port,
                OptionalInt.empty(),
                staticPeers,
                true,
                bootnodes,
                new TargetPeerRange(20, 30, 0),
                2);
        final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
        final ReputationManager reputationManager =
            new ReputationManager(
                metricsSystem,
                StubTimeProvider.withTimeInSeconds(1000),
                Constants.REPUTATION_MANAGER_CAPACITY);
        final DiscoveryNetwork<Peer> network =
            DiscoveryNetwork.create(
                metricsSystem,
                DelayedExecutorAsyncRunner.create(),
                new MemKeyValueStore<>(),
                new LibP2PNetwork(
                    DelayedExecutorAsyncRunner.create(),
                    config,
                    reputationManager,
                    METRICS_SYSTEM,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    (__1, __2) -> {
                      throw new UnsupportedOperationException();
                    },
                    topic -> true),
                new SimplePeerSelectionStrategy(config.getTargetPeerRange()),
                config);
        try {
          network.start().get(30, TimeUnit.SECONDS);
          networks.add(network);
          return network;
        } catch (final ExecutionException e) {
          if (e.getCause() instanceof BindException) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            Waiter.waitFor(network.stop());
          } else {
            throw e;
          }
        }
      }
    }
  }
}
