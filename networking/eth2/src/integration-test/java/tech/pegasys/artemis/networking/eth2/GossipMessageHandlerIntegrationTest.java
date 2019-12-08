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
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;

public class GossipMessageHandlerIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = new ChainStorageClient(eventBus1);
    final Eth2Network network1 =
        networkFactory
            .builder()
            .eventBus(eventBus1)
            .chainStorageClient(storageClient1)
            .startNetwork();
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(12, storageClient1);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = new ChainStorageClient(eventBus2);
    final Eth2Network network2 =
        networkFactory
            .builder()
            .eventBus(eventBus2)
            .chainStorageClient(storageClient2)
            .startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Setup network 3
    final EventBus eventBus3 = new EventBus();
    final ChainStorageClient storageClient3 = new ChainStorageClient(eventBus3);
    final Eth2Network network3 =
        networkFactory
            .builder()
            .eventBus(eventBus3)
            .chainStorageClient(storageClient3)
            .startNetwork();
    chainUtil.initializeStorage(storageClient3);

    // Connect networks 1 -> 2 -> 3
    network1.connect(network2.getNodeAddress());
    network2.connect(network3.getNodeAddress());
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(2);
          assertThat(network3.getPeerCount()).isEqualTo(1);
        });
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final BeaconBlock newBlock = chainUtil.createBlockAtSlot(UnsignedLong.valueOf(2L));
    eventBus1.post(newBlock);

    // Listen for new block event to arrive on networks 2 and 3
    final BeaconBlockCollector network2Blocks = new BeaconBlockCollector(eventBus2);
    final BeaconBlockCollector network3Blocks = new BeaconBlockCollector(eventBus3);

    // Verify the expected block was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(network2Blocks.getBlocks()).containsExactly(newBlock);
          assertThat(network3Blocks.getBlocks()).containsExactly(newBlock);
        });
  }

  @Test
  public void shouldNotGossipInvalidBlocks() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = new ChainStorageClient(eventBus1);
    final Eth2Network network1 =
        networkFactory
            .builder()
            .eventBus(eventBus1)
            .chainStorageClient(storageClient1)
            .startNetwork();
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(12, storageClient1);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = new ChainStorageClient(eventBus2);
    final Eth2Network network2 =
        networkFactory
            .builder()
            .eventBus(eventBus2)
            .chainStorageClient(storageClient2)
            .startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Setup network 3
    final EventBus eventBus3 = new EventBus();
    final ChainStorageClient storageClient3 = new ChainStorageClient(eventBus3);
    final Eth2Network network3 =
        networkFactory
            .builder()
            .eventBus(eventBus3)
            .chainStorageClient(storageClient3)
            .startNetwork();
    chainUtil.initializeStorage(storageClient3);

    // Connect networks 1 -> 2 -> 3
    network1.connect(network2.getNodeAddress());
    network2.connect(network3.getNodeAddress());
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(2);
          assertThat(network3.getPeerCount()).isEqualTo(1);
        });
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final BeaconBlock newBlock =
        chainUtil.createBlockAtSlotFromInvalidProposer(UnsignedLong.valueOf(2L));
    eventBus1.post(newBlock);

    // Listen for new block event to arrive on networks 2 and 3
    final BeaconBlockCollector network2Blocks = new BeaconBlockCollector(eventBus2);
    final BeaconBlockCollector network3Blocks = new BeaconBlockCollector(eventBus3);

    // Wait for blocks to propagate
    Thread.sleep(10_000);
    assertThat(network2Blocks.getBlocks()).isEmpty();
    assertThat(network3Blocks.getBlocks()).isEmpty();
  }

  private static class BeaconBlockCollector {
    private final Collection<BeaconBlock> blocks = new ConcurrentLinkedQueue<>();

    public BeaconBlockCollector(final EventBus eventBus) {
      eventBus.register(this);
    }

    @Subscribe
    public void onBeaconBlock(final BeaconBlock block) {
      blocks.add(block);
    }

    public Collection<BeaconBlock> getBlocks() {
      return blocks;
    }
  }
}
