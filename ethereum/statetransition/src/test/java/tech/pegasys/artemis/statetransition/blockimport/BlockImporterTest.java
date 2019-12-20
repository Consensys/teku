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

package tech.pegasys.artemis.statetransition.blockimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult.FailureReason;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class BlockImporterTest {
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(2);
  private final EventBus localEventBus = mock(EventBus.class);
  private final ChainStorageClient localStorage =
      ChainStorageClient.memoryOnlyClient(localEventBus);
  private final BeaconChainUtil localChain = BeaconChainUtil.create(localStorage, validatorKeys);

  private final EventBus otherEventBus = mock(EventBus.class);
  private final ChainStorageClient otherStorage =
      ChainStorageClient.memoryOnlyClient(otherEventBus);
  private final BeaconChainUtil otherChain = BeaconChainUtil.create(otherStorage, validatorKeys);

  private final BlockImporter blockImporter = new BlockImporter(localStorage, localEventBus);

  @BeforeEach
  public void setup() {
    otherChain.initializeStorage();
    localChain.initializeStorage();
  }

  @Test
  public void importBlock_success() throws Exception {
    final BeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block);
    assertThat(result).isEqualTo(BlockImportResult.SUCCESSFUL_RESULT);
  }

  @Test
  public void importBlock_fromFuture() throws Exception {
    final BeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.BLOCK_IS_FROM_FUTURE);
  }

  @Test
  public void importBlock_unknownParent() throws Exception {
    otherChain.createAndImportBlockAtSlot(UnsignedLong.ONE).getBlock();
    final BeaconBlock block2 =
        otherChain.createAndImportBlockAtSlot(UnsignedLong.valueOf(2)).getBlock();
    localChain.setSlot(block2.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block2);
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_wrongChain() throws Exception {
    UnsignedLong currentSlot = localStorage.getBestSlot();
    for (int i = 0; i < 3; i++) {
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      localChain.createAndImportBlockAtSlot(currentSlot);
    }
    // Update finalized epoch
    final Transaction tx = localStorage.startStoreTransaction();
    final Bytes32 bestRoot = localStorage.getBestBlockRoot();
    final UnsignedLong bestEpoch = compute_epoch_at_slot(localStorage.getBestSlot());
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized);
    tx.commit().join();

    // Now create a new block that is not descendent from the finalized block
    final BeaconBlock block = otherChain.createAndImportBlockAtSlot(UnsignedLong.ONE).getBlock();

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.INVALID_ANCESTRY);
  }

  @Test
  public void importBlock_invalidStateTransition() throws Exception {
    final BeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);
    block.setState_root(Bytes32.ZERO);
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.FAILED_STATE_TRANSITION);
  }

  private void assertImportFailed(
      final BlockImportResult result, final BlockImportResult.FailureReason expectedReason) {
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason()).isEqualTo(expectedReason);
    assertThat(result.getFailureCause()).isNotNull();
  }
}
