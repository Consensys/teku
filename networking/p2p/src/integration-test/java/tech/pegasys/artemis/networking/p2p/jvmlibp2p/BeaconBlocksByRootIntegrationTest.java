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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.network.p2p.jvmlibp2p.ChainStorageClientFactory.initChainStorageClient;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethod;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store.Transaction;

public class BeaconBlocksByRootIntegrationTest {

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
    final List<Bytes32> blockRoots = singletonList(Bytes32.ZERO);
    final List<BeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> response = requestBlocks(singletonList(Bytes32.ZERO));
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldReturnSingleBlockWhenOnlyOneMatches() throws Exception {
    initChainStorageClient(storageClient1);
    final BeaconBlock block = addBlock();

    final List<BeaconBlock> response = requestBlocks(singletonList(block.hash_tree_root()));
    assertThat(response).containsExactly(block);
  }

  @Test
  public void shouldReturnMultipleBlocksWhenAllRequestsMatch() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());
    final List<Bytes32> blockRoots =
        blocks.stream().map(BeaconBlock::hash_tree_root).collect(toList());
    final List<BeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMatchingBlocksWhenSomeRequestsDoNotMatch() throws Exception {
    initChainStorageClient(storageClient1);
    final List<BeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());

    // Real block roots interspersed with ones that don't match any blocks
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(BeaconBlock::hash_tree_root)
            .flatMap(hash -> Stream.of(Bytes32.fromHexStringLenient("0x123456789"), hash))
            .collect(toList());

    final List<BeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  private BeaconBlock addBlock() {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(seed, seed++);
    final Bytes32 blockRoot = block.hash_tree_root();
    final Transaction transaction = storageClient1.getStore().startTransaction();
    transaction.putBlock(blockRoot, block);
    transaction.commit();
    return block;
  }

  private List<BeaconBlock> requestBlocks(final List<Bytes32> blockRoots)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<BeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestStream(
            RpcMethod.BEACON_BLOCKS_BY_ROOT,
            new BeaconBlocksByRootRequestMessage(blockRoots),
            blocks::add));
    return blocks;
  }
}
