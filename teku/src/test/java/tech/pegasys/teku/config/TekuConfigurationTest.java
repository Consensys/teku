/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.BeaconNodeFacade;
import tech.pegasys.teku.TekuFacade;
import tech.pegasys.teku.cli.TempDirUtils;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.services.beaconchain.BeaconChainController;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory;

public class TekuConfigurationTest {

  Path tempDir = TempDirUtils.createTempDir();

  @AfterEach
  void cleanup() {
    TempDirUtils.deleteDirLenient(tempDir, 10, true);
  }

  @Test
  void beaconChainControllerFactory_useCustomFactories() {
    AtomicBoolean customDiscoveryBuilderMethodCalled = new AtomicBoolean();
    AtomicBoolean customLibP2PBuilderMethodCalled = new AtomicBoolean();
    AtomicBoolean customGossipNetworkBuilderCalled = new AtomicBoolean();

    DiscoveryNetworkBuilder customDiscoveryNetworkBuilder =
        new DiscoveryNetworkBuilder() {
          @Override
          public DiscoveryNetwork<?> build() {
            customDiscoveryBuilderMethodCalled.set(true);
            return super.build();
          }
        };

    LibP2PGossipNetworkBuilder customGossipNetworkBuilder =
        new LibP2PGossipNetworkBuilder() {
          @Override
          public LibP2PGossipNetwork build() {
            customGossipNetworkBuilderCalled.set(true);
            return super.build();
          }
        };

    LibP2PNetworkBuilder customLibP2PNetworkBuilder =
        new LibP2PNetworkBuilder() {
          @Override
          public P2PNetwork<Peer> build() {
            customLibP2PBuilderMethodCalled.set(true);
            return super.build();
          }

          @Override
          protected LibP2PGossipNetworkBuilder createLibP2PGossipNetworkBuilder() {
            return customGossipNetworkBuilder;
          }
        };

    Eth2P2PNetworkBuilder customEth2P2PNetworkBuilder =
        new Eth2P2PNetworkBuilder() {
          @Override
          protected DiscoveryNetworkBuilder createDiscoveryNetworkBuilder() {
            return customDiscoveryNetworkBuilder;
          }

          @Override
          protected LibP2PNetworkBuilder createLibP2PNetworkBuilder() {
            return customLibP2PNetworkBuilder;
          }
        };

    BeaconChainControllerFactory customControllerFactory =
        (serviceConfig, beaconConfig) ->
            new BeaconChainController(serviceConfig, beaconConfig) {
              @Override
              protected Eth2P2PNetworkBuilder createEth2P2PNetworkBuilder() {
                return customEth2P2PNetworkBuilder;
              }
            };

    TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .data(b -> b.dataBasePath(tempDir))
            .executionLayer(b -> b.engineEndpoint("unsafe-test-stub"))
            .beaconChainControllerFactory(customControllerFactory)
            .build();

    try (BeaconNodeFacade beaconNode = TekuFacade.startBeaconNode(tekuConfiguration)) {
      assertThat(beaconNode).isNotNull();
      assertThat(customDiscoveryBuilderMethodCalled).isTrue();
      assertThat(customLibP2PBuilderMethodCalled).isTrue();
      assertThat(customGossipNetworkBuilderCalled).isTrue();
    }
  }
}
