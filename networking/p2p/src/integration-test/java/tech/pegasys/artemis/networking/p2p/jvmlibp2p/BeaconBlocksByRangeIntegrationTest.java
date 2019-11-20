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
import tech.pegasys.artemis.datastructures.state.BeaconState;
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
    final List<BeaconBlock> response = requestBlocks(Bytes32.ZERO);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> response = requestBlocks(storageClient1.getBestBlockRoot());
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootDoesNotMatchAnyBlock() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> response = requestBlocks(Bytes32.ZERO);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootIsNotOnCanonicalChain() throws Exception {
    initChainStorageClient(storageClient1);
    final BeaconBlock nonCanonicalBlock = addBlock(1);
    final List<BeaconBlock> response = requestBlocks(nonCanonicalBlock.hash_tree_root());
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    initChainStorageClient(storageClient1);
    final BeaconBlock block1 = addBlock(1);
    final BeaconBlock block2 = addBlock(2);
    final Bytes32 block2Root = block2.hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    // Setup the best state.
    final BeaconState bestState = DataStructureUtil.randomBeaconState(block2.getSlot(), seed++);
    bestState.getBlock_roots().set(1, block1.hash_tree_root());
    bestState.getBlock_roots().set(2, block2Root);
    final Transaction transaction = storageClient1.getStore().startTransaction();
    transaction.putBlockState(block2Root, bestState);
    transaction.commit();

    final List<BeaconBlock> response = requestBlocks(block2Root);
    assertThat(response).containsExactly(block1, block2);
  }

  private BeaconBlock addBlock(final long slotNumber) {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slotNumber, seed++);
    final Bytes32 blockRoot = block.hash_tree_root();
    final Transaction transaction = storageClient1.getStore().startTransaction();
    transaction.putBlock(blockRoot, block);
    transaction.commit();
    return block;
  }

  private List<BeaconBlock> requestBlocks(final Bytes32 headBlockRoot)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<BeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestBlocksByRange(
            headBlockRoot,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(10),
            UnsignedLong.ONE,
            blocks::add));
    return blocks;
  }
}
