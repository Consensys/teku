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

package tech.pegasys.artemis.network.p2p.jvmlibp2p;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.P2PNetworkFactory.NetworkBuilder;
import tech.pegasys.artemis.networking.p2p.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.PeerHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Protocol;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;

public abstract class P2PNetworkFactory<
    T extends P2PNetwork, B extends P2PNetworkFactory<T, B>.NetworkBuilder> {

  protected static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<T> networks = new ArrayList<>();

  public abstract B builder();

  public void stopAll() {
    networks.forEach(P2PNetwork::stop);
  }

  public abstract class NetworkBuilder {
    protected List<T> peers = new ArrayList<>();
    protected EventBus eventBus;
    protected ChainStorageClient chainStorageClient;
    protected List<Protocol<?>> protocols = new ArrayList<>();
    protected List<PeerHandler> peerHandlers = new ArrayList<>();

    public T startNetwork() throws Exception {
      setDefaults();
      final T network = buildAndStartNetwork();
      networks.add(network);
      return network;
    }

    protected T buildAndStartNetwork() throws Exception {
      int attempt = 1;
      while (true) {
        final NetworkConfig config = generateConfig();
        final T network = buildNetwork(config);
        try {
          network.start().get(30, TimeUnit.SECONDS);
          networks.add(network);
          Waiter.waitFor(() -> assertThat(network.getPeerCount()).isEqualTo(peers.size()));
          return network;
        } catch (ExecutionException e) {
          if (e.getCause() instanceof BindException) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            network.stop();
          } else {
            throw e;
          }
        }
      }
    }

    protected abstract T buildNetwork(final NetworkConfig config);

    private NetworkConfig generateConfig() {
      final List<String> peerAddresses =
          peers.stream().map(P2PNetwork::getNodeAddress).collect(Collectors.toList());

      final Random random = new Random();
      final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);

      return new NetworkConfig(
          Optional.empty(), "127.0.0.1", port, port, peerAddresses, false, false, false);
    }

    private void setDefaults() {
      if (eventBus == null) {
        eventBus = new EventBus();
      }
      if (chainStorageClient == null) {
        chainStorageClient = new ChainStorageClient(eventBus);
      }
    }

    public NetworkBuilder peer(final T peer) {
      this.peers.add(peer);
      return this;
    }

    public NetworkBuilder eventBus(final EventBus eventBus) {
      checkNotNull(eventBus);
      this.eventBus = eventBus;
      return this;
    }

    public NetworkBuilder chainStorageClient(final ChainStorageClient chainStorageClient) {
      checkNotNull(chainStorageClient);
      this.chainStorageClient = chainStorageClient;
      return this;
    }

    public NetworkBuilder protocol(final Protocol<?> protocol) {
      checkNotNull(protocol);
      protocols.add(protocol);
      return this;
    }

    public NetworkBuilder peerHandler(final PeerHandler peerHandler) {
      checkNotNull(peerHandler);
      peerHandlers.add(peerHandler);
      return this;
    }
  }
}
