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
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconBlocksByRangeIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private ChainStorageClient storageClient1;
  private BeaconChainUtil beaconChainUtil;

  @BeforeEach
  public void setUp() throws Exception {
    final EventBus eventBus1 = new EventBus();
    storageClient1 = new ChainStorageClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();

    final Eth2Network network2 = networkFactory.peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    beaconChainUtil = BeaconChainUtil.create(1, storageClient1);
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
    beaconChainUtil.initializeStorage();
    final List<BeaconBlock> response = requestBlocks(storageClient1.getBestBlockRoot());
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootDoesNotMatchAnyBlock() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final List<BeaconBlock> response = requestBlocks(Bytes32.ZERO);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenHeadBlockRootIsNotOnCanonicalChain() throws Exception {
    beaconChainUtil.initializeStorage();
    final BeaconBlock nonCanonicalBlock = beaconChainUtil.createAndImportBlockAtSlot(1).getBlock();
    storageClient1.updateBestBlock(nonCanonicalBlock.getParent_root(), UnsignedLong.ZERO);
    final List<BeaconBlock> response = requestBlocks(nonCanonicalBlock.signing_root("signature"));
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    beaconChainUtil.initializeStorage();

    final BeaconBlock block1 = beaconChainUtil.createAndImportBlockAtSlot(1).getBlock();
    final Bytes32 block1Root = block1.signing_root("signature");
    storageClient1.updateBestBlock(block1Root, block1.getSlot());

    final BeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2).getBlock();
    final Bytes32 block2Root = block2.signing_root("signature");
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    final List<BeaconBlock> response = requestBlocks(block2Root);
    assertThat(response).containsExactly(block1, block2);
  }

  private List<BeaconBlock> requestBlocks(final Bytes32 headBlockRoot)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<BeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestBlocksByRange(
            headBlockRoot,
            UnsignedLong.ONE,
            UnsignedLong.valueOf(10),
            UnsignedLong.ONE,
            blocks::add));
    return blocks;
  }
}
