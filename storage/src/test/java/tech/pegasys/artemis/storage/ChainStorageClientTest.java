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

package tech.pegasys.artemis.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.util.config.Constants;

public class ChainStorageClientTest {

  private final ChainStorageClientSetup setup = new ChainStorageClientSetup();
  private final ChainStorageClient client = setup.getClient();

  @Test
  public void getBlockBySlot_noStore() {
    assertThat(client.getBlockBySlot(UnsignedLong.ONE)).isEmpty();
  }

  @Test
  public void getBlockBySlot_forGenesis() {
    // Setup chain
    setup.initForGenesis();
    final BeaconBlock genesisBlock = setup.genesisBlock().orElseThrow();
    client.updateBestBlock(genesisBlock.signing_root("signature"), genesisBlock.getSlot());

    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    assertThat(client.getBlockBySlot(genesisSlot)).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_forIndexedChainHead() {
    // Setup and index chain
    setup.initForGenesis();
    final List<BeaconBlock> chain =
        setup.createChain(Constants.GENESIS_SLOT + 1, Constants.GENESIS_SLOT + 5);
    final BeaconBlock chainHead = chain.get(chain.size() - 1);
    client.updateBestBlock(chainHead.signing_root("signature"), chainHead.getSlot());

    assertThat(client.getBlockBySlot(chainHead.getSlot())).contains(chainHead);
  }

  @Test
  public void getBlockBySlot_forPrunedBlock() {
    // Setup and index chain
    setup.initForGenesis();
    final List<BeaconBlock> chain =
        setup.createChain(Constants.GENESIS_SLOT + 1, Constants.GENESIS_SLOT + 5);
    final BeaconBlock chainHead = chain.get(chain.size() - 1);
    client.updateBestBlock(chainHead.signing_root("signature"), chainHead.getSlot());

    setup
        .getClient()
        .getStore()
        .cleanStoreUntilSlot(UnsignedLong.valueOf(Constants.GENESIS_SLOT + 3));

    UnsignedLong lookupSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT + 2);
    assertThat(client.getBlockBySlot(lookupSlot)).isEmpty();
  }

  @Test
  public void getBlockBySlot_futureSlot() {
    // Setup and index chain
    setup.initForGenesis();
    final List<BeaconBlock> chain =
        setup.createChain(Constants.GENESIS_SLOT + 1, Constants.GENESIS_SLOT + 5);
    final BeaconBlock chainHead = chain.get(chain.size() - 1);
    client.updateBestBlock(chainHead.signing_root("signature"), chainHead.getSlot());

    assertThat(client.getBlockBySlot(chainHead.getSlot().plus(UnsignedLong.ONE))).isEmpty();
  }

  @Test
  public void updateBestBlock_forNewChain() {
    // Setup genesis block
    setup.initForGenesis();
    final BeaconBlock genesisBlock = setup.genesisBlock().orElseThrow();

    // Add a new chain of blocks
    final List<BeaconBlock> newChain =
        setup.createChain(Constants.GENESIS_SLOT + 1, Constants.GENESIS_SLOT + 5);
    final BeaconBlock newHead = newChain.get(newChain.size() - 1);

    // Update client so that blocks are indexed
    client.updateBestBlock(newHead.signing_root("signature"), newHead.getSlot());

    // Verify that all blocks are indexed
    UnsignedLong currentSlot = genesisBlock.getSlot();
    assertThat(client.getBlockBySlot(genesisBlock.getSlot())).contains(genesisBlock);
    for (BeaconBlock newBlock : newChain) {
      // Sanity check that slots are sequential
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      assertThat(newBlock.getSlot()).isEqualTo(currentSlot);
      // Check that this block is indexed by slot
      assertThat(client.getBlockBySlot(newBlock.getSlot())).contains(newBlock);
    }
  }

  @Test
  public void updateBestBlock_toDifferentForkOfSameHeight() {
    testUpdateBestBlockToFork(3L, 3L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfLesserHeight() {
    testUpdateBestBlockToFork(5L, 3L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfGreaterHeight() {
    testUpdateBestBlockToFork(3L, 5L);
  }

  @Test
  public void updateBestBlock_toForkOfHeight1() {
    testUpdateBestBlockToFork(1L, 1L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfGreaterHeightWith_skippedBlocks() {
    testUpdateBestBlockToFork(9L, 15L, 2L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfSameHeightWith_skippedBlocks() {
    testUpdateBestBlockToFork(9L, 9L, 2L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfLesserHeightWith_skippedBlocks() {
    testUpdateBestBlockToFork(15L, 9L, 2L);
  }

  @Test
  public void
      updateBestBlock_toDifferentForkOfGreaterHeightWith_skippedBlocks_newChainFewerSkips() {
    testUpdateBestBlockToFork(9L, 15L, 2L, 1L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfGreaterHeightWith_skippedBlocks_newChainMoreSkips() {
    testUpdateBestBlockToFork(9L, 15L, 1L, 2L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfLesserHeightWith_skippedBlocks_newChainFewerSkips() {
    testUpdateBestBlockToFork(15L, 9L, 2L, 1L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfLesserHeightWith_skippedBlocks_newChainMoreSkips() {
    testUpdateBestBlockToFork(15L, 9L, 1L, 2L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfSameHeightWith_skippedBlocks_newChainFewerSkips() {
    testUpdateBestBlockToFork(9L, 9L, 2L, 1L);
  }

  @Test
  public void updateBestBlock_toDifferentForkOfSameHeightWith_skippedBlocks_newChainMoreSkips() {
    testUpdateBestBlockToFork(9L, 9L, 1L, 2L);
  }

  @Test
  public void updateBestBlock_whenBlocksHaveBeenPruned() {
    testUpdateBestBlockToFork(10L, 11L, 0, 0, 5L);
  }

  private void testUpdateBestBlockToFork(final long origChainLength, final long newChainLength) {
    testUpdateBestBlockToFork(origChainLength, newChainLength, 0, 0, 0L);
  }

  private void testUpdateBestBlockToFork(
      final long origChainLength,
      final long newChainLength,
      final long origChainSkippedSlots,
      final long newChainSkippedSlots) {
    testUpdateBestBlockToFork(
        origChainLength, newChainLength, origChainSkippedSlots, newChainSkippedSlots, 0L);
  }

  private void testUpdateBestBlockToFork(
      final long origChainLength, final long newChainLength, final long skippedSlots) {
    testUpdateBestBlockToFork(origChainLength, newChainLength, skippedSlots, skippedSlots, 0L);
  }

  private void testUpdateBestBlockToFork(
      final long origChainLength,
      final long newChainLength,
      final long origChainSkippedSlots,
      final long newChainSkippedSlots,
      final long pruneUpToSlot) {
    final UnsignedLong oldestAvailableSlot = UnsignedLong.valueOf(pruneUpToSlot);
    // Setup genesis block
    setup.initForGenesis();
    final BeaconBlock genesisBlock = setup.genesisBlock().orElseThrow();
    // Define chain parameters
    final long chainStartSlot = Constants.GENESIS_SLOT + 1L;

    // Add a new chain of blocks
    final List<BeaconBlock> oldChain =
        setup.createChain(chainStartSlot, chainStartSlot + origChainLength, origChainSkippedSlots);
    assertThat(oldChain.size())
        .isEqualTo(calculateBlocksInChain(origChainLength, origChainSkippedSlots));
    final BeaconBlock oldHead = oldChain.get(oldChain.size() - 1);
    // Update client so that blocks are indexed
    client.updateBestBlock(oldHead.signing_root("signature"), oldHead.getSlot());

    // Add another chain of blocks
    final List<BeaconBlock> newChain =
        setup.createChain(chainStartSlot, chainStartSlot + newChainLength, newChainSkippedSlots);
    assertThat(newChain.size())
        .isEqualTo(calculateBlocksInChain(newChainLength, newChainSkippedSlots));
    final BeaconBlock newHead = newChain.get(newChain.size() - 1);

    // Prune blocks if requested
    if (pruneUpToSlot >= Constants.GENESIS_SLOT) {
      final Store store = setup.getClient().getStore();
      store.cleanStoreUntilSlot(UnsignedLong.valueOf(pruneUpToSlot));
    }

    // Update client so that blocks are indexed
    client.updateBestBlock(newHead.signing_root("signature"), newHead.getSlot());

    // Verify that all non-pruned canonical blocks are indexed
    // Check genesis
    if (genesisBlock.getSlot().compareTo(oldestAvailableSlot) < 0) {
      assertThat(client.getBlockBySlot(genesisBlock.getSlot())).isEmpty();
    } else {
      assertThat(client.getBlockBySlot(genesisBlock.getSlot())).contains(genesisBlock);
    }
    // Check rest of chain
    final UnsignedLong newSlotDelta = UnsignedLong.valueOf(newChainSkippedSlots + 1);
    UnsignedLong currentSlot = UnsignedLong.valueOf(chainStartSlot);
    for (BeaconBlock newBlock : newChain) {
      // Sanity check that slots are sequential
      assertThat(newBlock.getSlot()).isEqualTo(currentSlot);
      // Check that this block is indexed by slot, if possible
      if (newBlock.getSlot().compareTo(oldestAvailableSlot) < 0) {
        assertThat(client.getBlockBySlot(newBlock.getSlot())).isEmpty();
      } else {
        assertThat(client.getBlockBySlot(newBlock.getSlot())).contains(newBlock);
      }
      currentSlot = currentSlot.plus(newSlotDelta);
    }

    // Verify that old blocks are no longer indexed
    currentSlot = UnsignedLong.valueOf(chainStartSlot);
    final UnsignedLong origSlotDelta = UnsignedLong.valueOf(origChainSkippedSlots + 1);
    for (BeaconBlock oldBlock : oldChain) {
      // Sanity check that slots are sequential
      assertThat(oldBlock.getSlot()).isEqualTo(currentSlot);

      // Check that old block is no longer indexed
      final UnsignedLong indexToCheck = UnsignedLong.valueOf(currentSlot.longValue());
      boolean shouldHaveBlockIndexedAtSlot =
          newChain.stream()
              .map(BeaconBlock::getSlot)
              .filter(slot -> slot.compareTo(oldestAvailableSlot) >= 0)
              .anyMatch(s -> Objects.equals(s, indexToCheck));
      if (shouldHaveBlockIndexedAtSlot) {
        assertThat(client.getBlockBySlot(oldBlock.getSlot()))
            .hasValueSatisfying(s -> assertThat(s).isNotEqualTo(oldBlock));
      } else {
        assertThat(client.getBlockBySlot(oldBlock.getSlot())).isEmpty();
      }
      currentSlot = currentSlot.plus(origSlotDelta);
    }
  }

  private long calculateBlocksInChain(final long chainLength, final long skippedBlocks) {
    // First block is always created
    return (chainLength - 1) / (skippedBlocks + 1) + 1;
  }
}
