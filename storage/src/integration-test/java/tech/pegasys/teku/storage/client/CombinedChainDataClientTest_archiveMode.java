/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.StateStorageMode;

public class CombinedChainDataClientTest_archiveMode extends AbstractCombinedChainDataClientTest {
  @Override
  protected StateStorageMode getStorageMode() {
    return StateStorageMode.ARCHIVE;
  }

  @Test
  public void getStateByStateRoot_shouldReturnFinalizedState()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain();
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    Optional<BeaconState> result =
        client.getStateByStateRoot(finalizedBlock.getState().hash_tree_root()).get();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(finalizedBlock.getState());
  }

  @Test
  public void getStateByStateRoot_shouldReturnFinalizedStateAtSkippedSlot()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    final UInt64 skippedSlot = historicalBlock.getSlot().plus(UInt64.ONE);
    chainUpdater.advanceChain(skippedSlot.plus(UInt64.ONE));
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();
    final BeaconState skippedSlotState = client.getStateAtSlotExact(skippedSlot).join().get();

    // Sanity check
    assertThat(skippedSlot).isLessThan(finalizedBlock.getSlot());
    assertThat(skippedSlot).isEqualTo(skippedSlotState.getSlot());

    Optional<BeaconState> result =
        client.getStateByStateRoot(skippedSlotState.hash_tree_root()).get();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(skippedSlotState);
  }
}
