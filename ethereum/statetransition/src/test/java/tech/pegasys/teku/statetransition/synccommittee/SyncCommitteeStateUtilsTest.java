/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncCommitteeStateUtilsTest {

  private static final int EPOCHS_PER_SYNC_COMMITTEE_PERIOD = 10;
  private static final UInt64 ALTAIR_FORK_SLOT = UInt64.valueOf(8);
  private final Spec spec =
      TestSpecFactory.createAltair(
          TestConfigLoader.loadConfig(
              "minimal",
              builder ->
                  builder.altairBuilder(
                      altairBuilder ->
                          altairBuilder
                              .altairForkSlot(ALTAIR_FORK_SLOT)
                              .epochsPerSyncCommitteePeriod(EPOCHS_PER_SYNC_COMMITTEE_PERIOD))));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(ALTAIR_FORK_SLOT);

  private final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final SyncCommitteeStateUtils stateUtils =
      new SyncCommitteeStateUtils(spec, recentChainData);

  @Test
  void shouldReturnEmptyWhenBlockSlotIsNotAvailable() {
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    assertThatSafeFuture(stateUtils.getStateForSyncCommittee(UInt64.ONE, blockRoot))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void shouldUseStateFromBeaconBlockRootWhenSlotEqualToBlockSlot() {
    final UInt64 slot = UInt64.valueOf(500);
    // State from same slot as block so definitely within the committee period.
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().slot(slot).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldUseStateFromBeaconBlockRootWhenBlockSlotWithinSameCommitteePeriod() {
    final UInt64 slot = UInt64.valueOf(500);
    // Few skip slots but still in the right region
    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(slot.minusMinZero(3)).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldUseStateFromBeaconBlockRootWhenBlockSlotInMinimumRequiredEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 stateSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch));
    // Few skip slots but still in the right region
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().slot(stateSlot).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldProcessSlotsIfStateIsWithinAnEpochOfForkSlot() {
    final UInt64 slot = ALTAIR_FORK_SLOT;
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    // Block is from before the Altair for sow we will need to process slots up to the fork slot
    final UInt64 blockSlot = UInt64.ZERO;
    final UInt64 stateSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch));
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(blockSlot));
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().slot(stateSlot).build();
    when(recentChainData.retrieveStateAtSlot(new SlotAndBlockRoot(stateSlot, blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconStateAltair>> result =
        stateUtils.getStateForSyncCommittee(slot, blockRoot);

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(state);
  }

  @Test
  void shouldReturnEmptyWhenBlockStateIsTooOld() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 blockSlot =
        spec.computeStartSlotAtEpoch(
                syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch).minus(1))
            .minus(1);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(blockSlot));

    assertThatSafeFuture(stateUtils.getStateForSyncCommittee(slot, blockRoot))
        .isCompletedWithEmptyOptional();
    verify(recentChainData, never()).retrieveStateAtSlot(any());
  }

  private void assertRetrievedStateIsSuitable(final UInt64 slot, final BeaconStateAltair state) {
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(state.getSlot()));
    when(recentChainData.retrieveStateAtSlot(new SlotAndBlockRoot(state.getSlot(), blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconStateAltair>> result =
        stateUtils.getStateForSyncCommittee(slot, blockRoot);

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(state);
  }
}
