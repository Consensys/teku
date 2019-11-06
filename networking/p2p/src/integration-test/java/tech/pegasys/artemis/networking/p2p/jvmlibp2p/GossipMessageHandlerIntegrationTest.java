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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeast;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class GossipMessageHandlerIntegrationTest {

  private final NetworkFactory networkFactory = new NetworkFactory();
  private final List<BLSKeyPair> validatorKeys =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(12);

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = new ChainStorageClient(eventBus1);
    final JvmLibP2PNetwork network1 = networkFactory.startNetwork(eventBus1, storageClient1);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(validatorKeys, storageClient1);

    // Setup network 2
    final EventBus eventBus2 = spy(new EventBus());
    final ChainStorageClient storageClient2 = new ChainStorageClient(eventBus2);
    final JvmLibP2PNetwork network2 = networkFactory.startNetwork(eventBus2, storageClient2);
    chainUtil.initializeStorage(storageClient2);

    // Setup network 3
    final EventBus eventBus3 = spy(new EventBus());
    final ChainStorageClient storageClient3 = new ChainStorageClient(eventBus3);
    final JvmLibP2PNetwork network3 = networkFactory.startNetwork(eventBus3, storageClient3);
    chainUtil.initializeStorage(storageClient3);

    // Connect networks 1 -> 2 -> 3
    network1.connect(network2.getPeerAddress());
    network2.connect(network3.getPeerAddress());
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerManager().getAvailablePeerCount()).isEqualTo(1);
          assertThat(network2.getPeerManager().getAvailablePeerCount()).isEqualTo(2);
          assertThat(network3.getPeerManager().getAvailablePeerCount()).isEqualTo(1);
        });
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final BeaconBlock newBlock = chainUtil.createBlockAtSlot(UnsignedLong.valueOf(2L));
    eventBus1.post(newBlock);

    // Listen for new block event to arrive on networks 2 and 3
    final ArgumentCaptor<BeaconBlock> blockCaptor = ArgumentCaptor.forClass(BeaconBlock.class);
    Waiter.waitFor(
        () -> {
          verify(eventBus2, atLeast(1)).post(blockCaptor.capture());
          verify(eventBus3, atLeast(1)).post(blockCaptor.capture());
        });

    // Verify the expected block was gossiped across the network
    final List<BeaconBlock> propagatedBlocks = blockCaptor.getAllValues();
    assertThat(propagatedBlocks.size()).isEqualTo(2);
    for (BeaconBlock propagatedBlock : propagatedBlocks) {
      assertThat(propagatedBlock).isEqualTo(newBlock);
    }
  }
}
