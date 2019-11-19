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
import static tech.pegasys.artemis.network.p2p.jvmlibp2p.ChainStorageClientFactory.initChainStorageClient;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store.Transaction;

public class BeaconBlocksByRangeIntegrationTest {

  private final NetworkFactory networkFactory = new NetworkFactory();
  private int seed = 1000;
  private Peer peer1;
  private ChainStorageClient storageClient1;

  @BeforeEach
  public void setUp() throws Exception {
    final EventBus eventBus1 = new EventBus();
    storageClient1 = new ChainStorageClient(eventBus1);
    final JvmLibP2PNetwork network1 = networkFactory.startNetwork(eventBus1, storageClient1);
    final JvmLibP2PNetwork network2 = networkFactory.startNetwork(network1);
    peer1 = network2.getPeerManager().getAvailablePeer(network1.getPeerId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldSendEmptyResponsePreGenesisEvent() throws Exception {
    final List<BeaconBlock> response = requestBlocks(Bytes32.ZERO, 1, 10, 1);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> response = requestBlocks(storageClient1.getBestBlockRoot(), 10, 10, 1);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootDoesNotMatchAnyBlock() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> response = requestBlocks(Bytes32.ZERO, 0, 10, 1);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootIsNotOnCanonicalChain() throws Exception {
    initChainStorageClient(storageClient1);
    final BeaconBlock nonCanonicalBlock = addBlock(1);
    final List<BeaconBlock> response = requestBlocks(nonCanonicalBlock.hash_tree_root(), 0, 10, 1);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() {}

  private BeaconBlock addBlock(final long slotNumber) {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slotNumber, seed++);
    final Bytes32 blockRoot = block.hash_tree_root();
    final Transaction transaction = storageClient1.getStore().startTransaction();
    transaction.putBlock(blockRoot, block);
    transaction.commit();
    return block;
  }

  private List<BeaconBlock> requestBlocks(
      final Bytes32 headBlockRoot, final long startSlot, final long count, final long step)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<BeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestBlocksByRange(
            headBlockRoot,
            UnsignedLong.valueOf(startSlot),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(step),
            blocks::add));
    return blocks;
  }
}
