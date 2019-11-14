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
    testUpdateBestBlockToFork(3L, 4L);
  }

  @Test
  public void updateBestBlock_toForkOfHeight1() {
    testUpdateBestBlockToFork(1L, 1L);
  }

  private void testUpdateBestBlockToFork(final long origChainLength, final long newChainLength) {
    // Setup genesis block
    setup.initForGenesis();
    final BeaconBlock genesisBlock = setup.genesisBlock().orElseThrow();
    // Define chain parameters
    final long chainStartSlot = Constants.GENESIS_SLOT + 1L;

    // Add a new chain of blocks
    final List<BeaconBlock> oldChain =
        setup.createChain(chainStartSlot, chainStartSlot + origChainLength);
    assertThat(oldChain.size()).isEqualTo(origChainLength);
    final BeaconBlock oldHead = oldChain.get(oldChain.size() - 1);
    // Update client so that blocks are indexed
    client.updateBestBlock(oldHead.signing_root("signature"), oldHead.getSlot());

    // Add another chain of blocks
    final List<BeaconBlock> newChain =
        setup.createChain(chainStartSlot, chainStartSlot + newChainLength);
    assertThat(newChain.size()).isEqualTo(newChainLength);
    final BeaconBlock newHead = newChain.get(newChain.size() - 1);
    // Update client so that blocks are indexed
    client.updateBestBlock(newHead.signing_root("signature"), newHead.getSlot());

    // Verify that all canonical blocks are indexed
    UnsignedLong currentSlot = genesisBlock.getSlot();
    assertThat(client.getBlockBySlot(genesisBlock.getSlot())).contains(genesisBlock);
    for (BeaconBlock newBlock : newChain) {
      // Sanity check that slots are sequential
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      assertThat(newBlock.getSlot()).isEqualTo(currentSlot);
      // Check that this block is indexed by slot
      assertThat(client.getBlockBySlot(newBlock.getSlot())).contains(newBlock);
    }

    // Verify that old blocks are no longer indexed
    currentSlot = genesisBlock.getSlot();
    for (BeaconBlock oldBlock : oldChain) {
      // Sanity check that slots are sequential
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      assertThat(oldBlock.getSlot()).isEqualTo(currentSlot);
      // Check that old block is no longer indexed
      if (currentSlot.compareTo(newHead.getSlot()) > 0) {
        assertThat(client.getBlockBySlot(oldBlock.getSlot())).isEmpty();
      } else {
        assertThat(client.getBlockBySlot(oldBlock.getSlot()))
            .hasValueSatisfying(s -> assertThat(s).isNotEqualTo(oldBlock));
      }
    }
  }
}
