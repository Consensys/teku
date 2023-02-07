/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.pruner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.Database;

public class BlobsSidecarPrunerTest {
  public static final Duration PRUNE_INTERVAL = Duration.ofSeconds(5);
  public static final int PRUNE_LIMIT = 10;

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
  private final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

  private UInt64 genesisTime = UInt64.valueOf(0);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final Database database = mock(Database.class);

  private final BlobsSidecarPruner blobsPruner =
      new BlobsSidecarPruner(
          spec, database, asyncRunner, timeProvider, PRUNE_INTERVAL, PRUNE_LIMIT);

  @BeforeEach
  void setUp() {
    when(database.getGenesisTime()).thenReturn(Optional.of(genesisTime));
    assertThat(blobsPruner.start()).isCompleted();
  }

  @Test
  void shouldNotPruneWhenGenesisNotAvailable() {
    when(database.getGenesisTime()).thenReturn(Optional.empty());

    asyncRunner.executeDueActions();

    verify(database).getGenesisTime();
    verify(database, never()).pruneOldestBlobsSidecar(any(), anyInt());
  }

  @Test
  void shouldNotPrunePriorGenesis() {
    asyncRunner.executeDueActions();

    verify(database).getGenesisTime();
    verify(database, never()).pruneOldestBlobsSidecar(any(), anyInt());
  }

  @Test
  void shouldNotPruneWhenLatestPrunableIncludeGenesis() {
    // set current slot inside the availability window
    final UInt64 currentSlot =
        UInt64.valueOf(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS).times(slotsPerEpoch);
    final UInt64 currentTime = currentSlot.times(secondsPerSlot);

    timeProvider.advanceTimeBy(Duration.ofSeconds(currentTime.longValue()));

    asyncRunner.executeDueActions();
    verify(database, never()).pruneOldestBlobsSidecar(any(), anyInt());
  }

  @Test
  void shouldPruneWhenLatestPrunableSlotIsGreaterThanOldestDAEpoch() {
    // set current slot to MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS + 1 epoch + half epoch
    final UInt64 currentSlot =
        UInt64.valueOf(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS + 1)
            .times(slotsPerEpoch)
            .plus(slotsPerEpoch / 2);
    final UInt64 currentTime = currentSlot.times(secondsPerSlot);

    timeProvider.advanceTimeBy(Duration.ofSeconds(currentTime.longValue()));

    asyncRunner.executeDueActions();
    verify(database).pruneOldestBlobsSidecar(UInt64.valueOf((slotsPerEpoch / 2) - 1), PRUNE_LIMIT);
  }

  @Test
  void shouldNotPruneUnconfirmedBlobsWithoutFinalizedCheckpoint() {
    asyncRunner.executeDueActions();

    verify(database, never()).pruneOldestUnconfirmedBlobsSidecars(any(), anyInt());
  }

  @Test
  void shouldPruneUnconfirmedBlobsWithFinalizedCheckpoint() {
    blobsPruner.onNewFinalizedCheckpoint(dataStructureUtil.randomCheckpoint(1), false);
    asyncRunner.executeDueActions();

    final UInt64 expectedLastSlotToPrune =
        UInt64.valueOf(spec.getGenesisSpecConfig().getSlotsPerEpoch());

    verify(database).pruneOldestUnconfirmedBlobsSidecars(expectedLastSlotToPrune, PRUNE_LIMIT);

    timeProvider.advanceTimeBy(PRUNE_INTERVAL);
    asyncRunner.executeDueActions();

    verify(database, times(1))
        .pruneOldestUnconfirmedBlobsSidecars(expectedLastSlotToPrune, PRUNE_LIMIT);
  }
}
