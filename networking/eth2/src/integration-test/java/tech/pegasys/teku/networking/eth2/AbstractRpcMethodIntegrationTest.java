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

package tech.pegasys.teku.networking.eth2;

import org.junit.jupiter.api.AfterEach;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public abstract class AbstractRpcMethodIntegrationTest {
  protected final UInt64 altairSlot = UInt64.valueOf(16); // Epoch 2 for minimal
  protected StorageSystem peerStorage;

  private final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
  private final Spec altairEnabledSpec = TestSpecFactory.createMinimalWithAltairFork(altairSlot);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  protected Eth2Peer createNetworks() {
    return createNetworks(false, false);
  }

  protected void setupPeerStorage(final boolean enableAltair) {
    final Spec remoteSpec = enableAltair ? altairEnabledSpec : phase0Spec;
    peerStorage = InMemoryStorageSystemBuilder.create().specProvider(remoteSpec).build();
    peerStorage.chainUpdater().initializeGenesis();
  }

  /**
   * Create and connect 2 networks, return an Eth2Peer representing the remote network to which we
   * can send requests.
   *
   * @param enableAltairLocally Whether the "local" node supports altair
   * @param enableAltairRemotely Whether the remote peer receiving requests supports altair
   * @return An Eth2Peer to which we can send requests
   */
  protected Eth2Peer createNetworks(
      final boolean enableAltairLocally, final boolean enableAltairRemotely) {
    // Set up remote peer storage
    final Spec remoteSpec = enableAltairRemotely ? altairEnabledSpec : phase0Spec;
    if (peerStorage == null) {
      peerStorage = InMemoryStorageSystemBuilder.create().specProvider(remoteSpec).build();
      peerStorage.chainUpdater().initializeGenesis();
    }

    // Set up local storage
    final Spec localSpec = enableAltairLocally ? altairEnabledSpec : phase0Spec;
    final StorageSystem localStorage =
        InMemoryStorageSystemBuilder.create().specProvider(localSpec).build();
    localStorage.chainUpdater().initializeGenesis();

    try {
      final Eth2P2PNetwork remotePeerNetwork =
          networkFactory
              .builder()
              .rpcEncoding(RpcEncoding.SSZ_SNAPPY)
              .eventBus(peerStorage.eventBus())
              .recentChainData(peerStorage.recentChainData())
              .historicalChainData(peerStorage.chainStorage())
              .spec(remoteSpec)
              .startNetwork();

      final Eth2P2PNetwork localNetwork =
          networkFactory
              .builder()
              .rpcEncoding(RpcEncoding.SSZ_SNAPPY)
              .peer(remotePeerNetwork)
              .recentChainData(localStorage.recentChainData())
              .historicalChainData(localStorage.chainStorage())
              .spec(localSpec)
              .startNetwork();

      return localNetwork.getPeer(remotePeerNetwork.getNodeId()).orElseThrow();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
