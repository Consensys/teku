/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase.DataColumnSidecarType;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;

class KvStoreDatabaseTest {

  // Data column sidecar pruning is forward (oldest-first): each run removes the oldest up-to-limit
  // distinct populated slots at or before the cutoff, so the earliest retained slot keeps advancing
  // and no gap can form when sidecars are stored faster than they can be pruned. An in-memory
  // frontier lets consecutive runs continue from where the previous run stopped instead of
  // re-scanning already-pruned slots.

  private final NavigableSet<UInt64> populatedSlots = new TreeSet<>();
  private final List<SlotRange> streamedRanges = new ArrayList<>();
  private final List<UInt64> removedSlots = new ArrayList<>();
  private final AtomicInteger commits = new AtomicInteger();

  @Test
  void prunesOldestDistinctSlotsInASingleCommit() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).containsExactly(UInt64.valueOf(28), UInt64.valueOf(29));
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @Test
  void prunesOnlyUpToTheLimitDistinctSlots() {
    final KvStoreDatabase database = database(UInt64.ZERO, 10, 11, 12, 13, 14);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);

    // Only the oldest 2 slots (10 and 11) are pruned.
    assertThat(removedSlots).containsExactly(UInt64.valueOf(10), UInt64.valueOf(11));
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @Test
  void prunesDistinctSlotsAcrossGaps() {
    // Only slots 1 and 100 are populated; with a limit of 2 both must be pruned even though there
    // are no slots in between them.
    final KvStoreDatabase database = database(UInt64.ZERO, 1, 100);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(100), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).containsExactly(UInt64.ONE, UInt64.valueOf(100));
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @Test
  void stopsWhenFewerThanLimitDistinctSlotsAreAvailable() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 10, 11, 12);

    final boolean limitReached =
        database.pruneDataColumnSidecars(10, UInt64.valueOf(12), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots)
        .containsExactly(UInt64.valueOf(10), UInt64.valueOf(11), UInt64.valueOf(12));
    assertThat(commits).hasValue(1);
    // Fewer distinct slots than the limit were available, so the limit was not the reason we
    // stopped.
    assertThat(limitReached).isFalse();
  }

  @Test
  void coldStartScanBeginsAtFirstSupportedSlot() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    // With no frontier cached yet, the forward scan starts at the first slot with data column
    // sidecar support.
    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(10), UInt64.valueOf(30)));
  }

  @Test
  void frontierAdvancesSoConsecutiveRunsDoNotRescanPrunedSlots() {
    final KvStoreDatabase database = database(UInt64.ZERO, 10, 11, 12, 13, 14);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);
    database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);

    // Run 1 prunes 10,11 then run 2 continues from 12 (the frontier), never re-scanning from 0.
    assertThat(removedSlots)
        .containsExactly(
            UInt64.valueOf(10), UInt64.valueOf(11), UInt64.valueOf(12), UInt64.valueOf(13));
    assertThat(streamedRanges)
        .containsExactly(
            new SlotRange(UInt64.ZERO, UInt64.valueOf(14)),
            new SlotRange(UInt64.valueOf(12), UInt64.valueOf(14)));
    assertThat(commits).hasValue(2);
  }

  @Test
  void doesNothingWhenNoSidecarsAtOrBeforeCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(10));

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).isEmpty();
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  @Test
  void doesNothingWhenFirstSupportedSlotIsAfterCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(40), 50);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(streamedRanges).isEmpty();
    assertThat(removedSlots).isEmpty();
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  private KvStoreDatabase database(final UInt64 firstFuluSlot, final long... slots) {
    for (final long slot : slots) {
      populatedSlots.add(UInt64.valueOf(slot));
    }
    final Spec spec = mock(Spec.class);
    when(spec.computeFirstSlotWithDataColumnSidecarSupport())
        .thenReturn(Optional.of(firstFuluSlot));
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    return new KvStoreDatabase(dao, StateStorageMode.PRUNE, false, spec) {

      @Override
      protected FinalizedUpdater finalizedUpdater() {
        return countingUpdater();
      }

      @Override
      public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
          final UInt64 firstSlot, final UInt64 lastSlot) {
        streamedRanges.add(new SlotRange(firstSlot, lastSlot));
        return populatedSlots.subSet(firstSlot, true, lastSlot, true).stream()
            .map(slot -> identifier(slot.longValue(), 0));
      }
    };
  }

  private FinalizedUpdater countingUpdater() {
    final FinalizedUpdater updater = mock(FinalizedUpdater.class);
    doAnswer(
            (final InvocationOnMock invocation) -> {
              final DataColumnSlotAndIdentifier key = invocation.getArgument(0);
              removedSlots.add(key.slot());
              populatedSlots.remove(key.slot());
              return null;
            })
        .when(updater)
        .removeSidecar(any());
    doAnswer(
            (final InvocationOnMock __) -> {
              commits.incrementAndGet();
              return null;
            })
        .when(updater)
        .commit();
    return updater;
  }

  private static DataColumnSlotAndIdentifier identifier(final long slot, final long columnIndex) {
    return new DataColumnSlotAndIdentifier(
        UInt64.valueOf(slot), Bytes32.ZERO, UInt64.valueOf(columnIndex));
  }

  private record SlotRange(UInt64 firstSlot, UInt64 lastSlot) {}
}
