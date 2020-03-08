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

package tech.pegasys.artemis.networking.eth2;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.p2p.DiscoveryNetwork;
import tech.pegasys.artemis.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.Waiter;

public class Eth2NetworkFactory {

  protected static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<Eth2Network> networks = new ArrayList<>();

  public Eth2P2PNetworkBuilder builder() {
    return new Eth2P2PNetworkBuilder();
  }

  public void stopAll() {
    networks.forEach(P2PNetwork::stop);
  }

  public class Eth2P2PNetworkBuilder {

    protected List<Eth2Network> peers = new ArrayList<>();
    protected EventBus eventBus;
    protected ChainStorageClient chainStorageClient;
    protected List<RpcMethod> rpcMethods = new ArrayList<>();
    protected List<PeerHandler> peerHandlers = new ArrayList<>();

    public Eth2Network startNetwork() throws Exception {
      setDefaults();
      final Eth2Network network = buildAndStartNetwork();
      networks.add(network);
      return network;
    }

    protected Eth2Network buildAndStartNetwork() throws Exception {
      int attempt = 1;
      while (true) {
        final NetworkConfig config = generateConfig();
        final Eth2Network network = buildNetwork(config);
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

    protected Eth2Network buildNetwork(final NetworkConfig config) {
      {
        // Setup eth2 handlers
        final HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);
        final Eth2PeerManager eth2PeerManager =
            Eth2PeerManager.create(chainStorageClient, historicalChainData, METRICS_SYSTEM);
        final Collection<RpcMethod> eth2Protocols = eth2PeerManager.getBeaconChainMethods().all();
        // Configure eth2 handlers
        this.rpcMethods(eth2Protocols).peerHandler(eth2PeerManager);

        final P2PNetwork<?> network =
            DiscoveryNetwork.create(
                new LibP2PNetwork(config, METRICS_SYSTEM, rpcMethods, peerHandlers), config);

        return new Eth2Network(network, eth2PeerManager, eventBus, chainStorageClient);
      }
    }

    private NetworkConfig generateConfig() {
      final List<String> peerAddresses =
          peers.stream().map(P2PNetwork::getNodeAddress).collect(toList());

      final Random random = new Random();
      final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);

      return new NetworkConfig(
          KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1(),
          "127.0.0.1",
          "127.0.0.1",
          port,
          port,
          peerAddresses,
          "static",
          emptyList(),
          new TargetPeerRange(20, 30),
          false,
          false,
          false);
    }

    private void setDefaults() {
      if (eventBus == null) {
        eventBus = new EventBus();
      }
      if (chainStorageClient == null) {
        chainStorageClient = ChainStorageClient.memoryOnlyClient(eventBus);
      }
    }

    public Eth2P2PNetworkBuilder peer(final Eth2Network peer) {
      this.peers.add(peer);
      return this;
    }

    public Eth2P2PNetworkBuilder eventBus(final EventBus eventBus) {
      checkNotNull(eventBus);
      this.eventBus = eventBus;
      return this;
    }

    public Eth2P2PNetworkBuilder chainStorageClient(final ChainStorageClient chainStorageClient) {
      checkNotNull(chainStorageClient);
      this.chainStorageClient = chainStorageClient;
      return this;
    }

    public Eth2P2PNetworkBuilder rpcMethods(final Collection<RpcMethod> methods) {
      checkNotNull(methods);
      this.rpcMethods.addAll(methods);
      return this;
    }

    public Eth2P2PNetworkBuilder peerHandler(final PeerHandler peerHandler) {
      checkNotNull(peerHandler);
      peerHandlers.add(peerHandler);
      return this;
    }
  }
}
