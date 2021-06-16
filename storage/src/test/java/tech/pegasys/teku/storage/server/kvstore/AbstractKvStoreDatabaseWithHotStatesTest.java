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

package tech.pegasys.teku.storage.server.kvstore;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.store.StoreConfig;

public abstract class AbstractKvStoreDatabaseWithHotStatesTest extends AbstractKvStoreDatabaseTest {

  @Test
  public void shouldPersistHotStates_everyEpoch() {
    final int storageFrequency = 1;
    StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(storageFrequency).build();
    createStorage(StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();

    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = compute_start_slot_at_epoch(latestEpoch);
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(1, latestEpoch.intValue());
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      if (epochBoundarySlots.contains(UInt64.valueOf(i))) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @Test
  public void shouldPersistHotStates_never() {
    final int storageFrequency = 0;
    StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(storageFrequency).build();
    createStorage(StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();

    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = compute_start_slot_at_epoch(latestEpoch);
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  @Test
  public void shouldPersistHotStates_everyThirdEpoch() {
    final int storageFrequency = 3;
    StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(storageFrequency).build();
    createStorage(StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();

    final UInt64 latestEpoch = UInt64.valueOf(3 * storageFrequency);
    final UInt64 targetSlot = compute_start_slot_at_epoch(latestEpoch);
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(1, latestEpoch.intValue());
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      final UInt64 currentSlot = UInt64.valueOf(i);
      final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);
      final boolean shouldPersistThisEpoch = currentEpoch.mod(storageFrequency).equals(UInt64.ZERO);
      if (epochBoundarySlots.contains(currentSlot) && shouldPersistThisEpoch) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @Test
  public void shouldClearStaleHotStates() {
    final int storageFrequency = 1;
    StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(storageFrequency).build();
    createStorage(StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();

    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = compute_start_slot_at_epoch(latestEpoch);
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));
    justifyAndFinalizeEpoch(latestEpoch, chainBuilder.getLatestBlockAndState());

    // Hot states should be cleared out
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  private Set<UInt64> getEpochBoundarySlots(final int fromEpoch, final int toEpoch) {
    final Set<UInt64> epochBoundarySlots = new HashSet<>();
    for (int i = fromEpoch; i <= toEpoch; i++) {
      final UInt64 epochSlot = compute_start_slot_at_epoch(UInt64.valueOf(i));
      epochBoundarySlots.add(epochSlot);
    }
    return epochBoundarySlots;
  }
}
