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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityInitializerTest {
  private final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
  private final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
  private final WeakSubjectivityConfig defaultConfig = WeakSubjectivityConfig.builder().build();
  private final WeakSubjectivityInitializer initializer = new WeakSubjectivityInitializer();

  @BeforeEach
  public void setup() {
    when(updateChannel.onWeakSubjectivityUpdate(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void finalizeAndStoreConfig_nothingStored_noNewArgs() {
    // Nothing is stored
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(WeakSubjectivityState.empty()));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(
            defaultConfig, Optional.empty(), queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());
    assertThat(result.join()).usingRecursiveComparison().isEqualTo(defaultConfig);
  }

  @Test
  public void finalizeAndStoreConfig_nothingStored_withNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();

    // Nothing is stored
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(WeakSubjectivityState.empty()));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(
            cliConfig, Optional.empty(), queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));
    assertThat(result.join()).usingRecursiveComparison().isEqualTo(cliConfig);
  }

  @Test
  public void finalizeAndStoreConfig_withStoredCheckpoint_noNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    // Setup storage
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(storedCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(
            defaultConfig, Optional.empty(), queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());
    assertThat(result.join())
        .usingRecursiveComparison()
        .isEqualTo(WeakSubjectivityConfig.from(storedState));
  }

  @Test
  public void finalizeAndStoreConfig_withStoredCheckpoint_withNewDistinctArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder()
            .weakSubjectivityCheckpoint(cliCheckpoint)
            .suppressWSPeriodChecksUntilEpoch(UInt64.valueOf(123))
            .safetyDecay(UInt64.valueOf(5))
            .build();

    // Setup storage
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(storedCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(
            cliConfig, Optional.empty(), queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));
    assertThat(result.join()).usingRecursiveComparison().isEqualTo(cliConfig);
  }

  @Test
  public void finalizeAndStoreConfig_withStoredCheckpoint_withConsistentCLIArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();

    // Setup storage
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(cliCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(
            cliConfig, Optional.empty(), queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());
    assertThat(result.join()).usingRecursiveComparison().isEqualTo(cliConfig);
  }

  @Test
  public void finalizeAndStoreConfig_anchorAtSameEpochAsCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(cliCheckpoint.getEpoch());

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void finalizeAndStoreConfig_anchorAfterCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(wsCheckpointEpoch.plus(2));

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void finalizeAndStoreConfig_anchorPriorToCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(wsCheckpointEpoch.minus(2));

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isPresent();
  }

  @Test
  public void finalizeAndStoreConfig_anchorAtSameEpochAsStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(storedCheckpoint.getEpoch());

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void finalizeAndStoreConfig_anchorAfterStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(wsCheckpointEpoch.plus(2));

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void finalizeAndStoreConfig_anchorPriorToStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(
            compute_start_slot_at_epoch(wsCheckpointEpoch.minus(2)));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testFinalizeAndStoreConfigWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isPresent();
  }

  private Optional<Checkpoint> testFinalizeAndStoreConfigWithAnchor(
      Optional<Checkpoint> storedCheckpoint,
      Optional<Checkpoint> cliCheckpoint,
      Optional<AnchorPoint> wsInitialAnchor) {
    // Set up expectations
    final Optional<Checkpoint> expectedWsCheckpoint =
        cliCheckpoint
            .or(() -> storedCheckpoint)
            .filter(
                c ->
                    wsInitialAnchor.isEmpty()
                        || wsInitialAnchor.get().getEpoch().isLessThan(c.getEpoch()));
    final boolean shouldPersistWsCheckpoint =
        cliCheckpoint.isPresent() && expectedWsCheckpoint.isPresent();
    final boolean shouldClearWsCheckpoint =
        storedCheckpoint.isPresent() && expectedWsCheckpoint.isEmpty();
    final WeakSubjectivityConfig expectedConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(expectedWsCheckpoint).build();

    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();

    // Setup storage
    final WeakSubjectivityState storedState = WeakSubjectivityState.create(storedCheckpoint);
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    final SafeFuture<WeakSubjectivityConfig> result =
        initializer.finalizeAndStoreConfig(cliConfig, wsInitialAnchor, queryChannel, updateChannel);

    assertThat(result).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    if (shouldPersistWsCheckpoint) {
      verify(updateChannel)
          .onWeakSubjectivityUpdate(
              WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(
                  expectedWsCheckpoint.orElseThrow()));
    } else if (shouldClearWsCheckpoint) {
      verify(updateChannel)
          .onWeakSubjectivityUpdate(WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint());
    } else {
      verify(updateChannel, never()).onWeakSubjectivityUpdate(any());
    }
    assertThat(result.join()).usingRecursiveComparison().isEqualTo(expectedConfig);

    return expectedWsCheckpoint;
  }

  @Test
  public void validateInitialAnchor_forGenesisAtGenesisSlot() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(0);

    // Should not throw
    initializer.validateInitialAnchor(anchor, UInt64.ZERO);
  }

  @Test
  public void validateInitialAnchor_forGenesisAfterGenesisSlot() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(0);

    // Should not throw
    initializer.validateInitialAnchor(anchor, UInt64.valueOf(10));
  }

  @Test
  public void validateInitialAnchor_forInitialStateFromFuture() {
    final UInt64 currentEpoch = UInt64.valueOf(10);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch);

    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(currentEpoch.plus(1));

    assertThatThrownBy(() -> initializer.validateInitialAnchor(anchor, currentSlot))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "The provided initial state appears to be from a future slot ("
                + anchor.getBlockSlot()
                + ").");
  }

  @Test
  public void validateInitialAnchor_forEpochTooRecentToBeFinal() {
    final UInt64 currentEpoch = UInt64.valueOf(10);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch).plus(1);

    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(currentEpoch.minus(1));

    assertThatThrownBy(() -> initializer.validateInitialAnchor(anchor, currentSlot))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "The provided initial state is too recent. Please check that the initial state corresponds to a finalized checkpoint.");
  }

  @Test
  public void validateInitialAnchor_atMostRecentPotentiallyFinalizedEpoch() {
    final UInt64 currentEpoch = UInt64.valueOf(10);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch);

    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final AnchorPoint anchor = dataStructureUtil.randomAnchorPoint(currentEpoch.minus(2));

    // Should not throw
    initializer.validateInitialAnchor(anchor, currentSlot);
  }
}
