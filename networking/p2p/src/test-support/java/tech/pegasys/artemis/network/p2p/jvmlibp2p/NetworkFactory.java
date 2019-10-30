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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Config;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

public class NetworkFactory {

  private static final Logger LOG = LogManager.getLogger();
  private static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<JvmLibP2PNetwork> networks = new ArrayList<>();

  public JvmLibP2PNetwork startNetwork(final JvmLibP2PNetwork... peers)
      throws TimeoutException, InterruptedException, ExecutionException {
    return startNetwork(new EventBus(), peers);
  }

  public JvmLibP2PNetwork startNetwork(final EventBus eventBus, final JvmLibP2PNetwork... peers)
      throws TimeoutException, InterruptedException, ExecutionException {
    return startNetwork(eventBus, new ChainStorageClient(eventBus), peers);
  }

  public JvmLibP2PNetwork startNetwork(
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient,
      final JvmLibP2PNetwork... peers)
      throws TimeoutException, InterruptedException, ExecutionException {
    final Random random = new Random();
    final List<String> peerAddresses =
        Stream.of(peers).map(JvmLibP2PNetwork::getPeerAddress).collect(Collectors.toList());
    int attempt = 1;
    while (true) {
      int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);
      final JvmLibP2PNetwork network =
          new JvmLibP2PNetwork(
              new Config(
                  Optional.empty(), "127.0.0.1", port, port, peerAddresses, false, false, false),
              eventBus,
              chainStorageClient,
              METRICS_SYSTEM);
      try {
        network.start().get(30, TimeUnit.SECONDS);
        networks.add(network);
        Waiter.waitFor(
            () ->
                assertThat(network.getPeerManager().getAvailablePeerCount())
                    .isEqualTo(peers.length));
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

  public void stopAll() {
    networks.forEach(JvmLibP2PNetwork::stop);
  }
}
