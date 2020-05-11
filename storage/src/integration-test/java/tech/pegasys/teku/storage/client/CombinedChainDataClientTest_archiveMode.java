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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.storage.InMemoryStorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class CombinedChainDataClientTest_archiveMode extends AbstractCombinedChainDataClientTest {
  @Override
  protected InMemoryStorageSystem createStorageSystem() {
    return InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.ARCHIVE);
  }

  @Test
  public void getStateAtSlot_shouldRetrieveHistoricalState() {
    final CombinedChainDataClient client = storageSystem.combinedChainDataClient();

    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    final SignedBlockAndState blockAtEpoch = chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(historicalBlock.getSlot()).isLessThan(finalizedBlock.getSlot());

    assertThat(client.getStateAtSlot(historicalBlock.getSlot()))
        .isCompletedWithValue(Optional.of(historicalBlock.getState()));
  }
}
