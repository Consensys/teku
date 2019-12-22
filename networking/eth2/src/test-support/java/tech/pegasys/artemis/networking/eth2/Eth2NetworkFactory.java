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
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;

public class Eth2NetworkFactory {

  protected static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  protected static final int MIN_PORT = 6000;
  protected static final int MAX_PORT = 9000;
  protected List<Eth2Network> multiaddrpeers = new ArrayList<>();

  protected final List<Eth2Network> networks = new ArrayList<>();

  Eth2NetworkBuilder eth2NetworkBuilder;

  public Eth2NetworkFactory() {
    eth2NetworkBuilder = Eth2NetworkBuilder.create();
  }

  public Eth2Network startNetwork() throws Exception {

    final Eth2Network network = buildAndStartNetwork();
    networks.add(network);
    return network;
  }

  private NetworkConfig generateConfig() {
    final List<String> peerAddresses =
        multiaddrpeers.stream().map(P2PNetwork::getNodeAddress).collect(Collectors.toList());

    final Random random = new Random();
    final int port =
        Eth2NetworkFactory.MIN_PORT
            + random.nextInt(Eth2NetworkFactory.MAX_PORT - Eth2NetworkFactory.MIN_PORT);

    return new NetworkConfig(
        Optional.empty(), "127.0.0.1", port, port, peerAddresses, false, false, false);
  }

  protected Eth2Network buildAndStartNetwork() throws Exception {
    int attempt = 1;
    while (true) {
      final Eth2Network network = buildNetwork();
      try {
        network.start().get(30, TimeUnit.SECONDS);
        networks.add(network);
        Waiter.waitFor(() -> assertThat(network.getPeerCount()).isEqualTo(multiaddrpeers.size()));
        return network;
      } catch (ExecutionException e) {
        if (e.getCause() instanceof BindException) {
          if (attempt > 10) {
            throw new RuntimeException("Failed to find a free port after multiple attempts", e);
          }
          Eth2NetworkFactory.LOG.info(
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

  public void stopAll() {
    networks.forEach(
        n -> {
          n.stop();
        });
  }

  protected EventBus eventBus;
  protected ChainStorageClient chainStorageClient;

  public Eth2NetworkFactory eventBus(EventBus eventBus) {
    this.eventBus = eventBus;
    return this;
  }

  public Eth2NetworkFactory peer(Eth2Network peer) {
    this.multiaddrpeers.add(peer);
    return this;
  }

  public Eth2NetworkFactory chainStorageClient(ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
    return this;
  }

  private void setDefaults() {
    if (eventBus == null) {
      eventBus = new EventBus();
    }
    if (chainStorageClient == null) {
      chainStorageClient = ChainStorageClient.memoryOnlyClient(eventBus);
    }
  }

  public Eth2Network buildNetwork() {
    final NetworkConfig config = generateConfig();
    return buildNetwork(config);
  }

  public Eth2Network buildNetwork(final NetworkConfig config) {
    {
      setDefaults();

      eth2NetworkBuilder.config(config);
      eth2NetworkBuilder.eventBus(eventBus);
      eth2NetworkBuilder.chainStorageClient(chainStorageClient);
      eth2NetworkBuilder.metricsSystem(METRICS_SYSTEM);
      eth2NetworkBuilder.discovery(generateConfig());

      return eth2NetworkBuilder.build();
    }
  }
}
