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

  private final List<UInt64> lookupSlots = new ArrayList<>();
  private final List<SlotRange> streamedRanges = new ArrayList<>();
  private final AtomicInteger removals = new AtomicInteger();
  private final AtomicInteger commits = new AtomicInteger();

  @Test
  void pruneDataColumnSidecarsPrunesNewestDistinctSlotsInASingleCommit() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    // The two newest populated slots at or before the cutoff are removed in one range scan + commit
    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(29), UInt64.valueOf(30)));
    assertThat(removals).hasValue(2);
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @Test
  void pruneDataColumnSidecarsPrunesDistinctSlotsAcrossGaps() {
    // Only slots 1 and 100 are populated; with a limit of 2 both must be pruned even though there
    // are no slots in between them.
    final KvStoreDatabase database = database(UInt64.ZERO, 1, 100);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(100), DataColumnSidecarType.CANONICAL);

    // A single range scan spans the gap between the two populated slots
    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(1), UInt64.valueOf(100)));
    assertThat(removals).hasValue(2);
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @Test
  void pruneDataColumnSidecarsStopsAtFirstSupportedSlot() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 10, 11, 12);

    final boolean limitReached =
        database.pruneDataColumnSidecars(10, UInt64.valueOf(12), DataColumnSidecarType.CANONICAL);

    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(10), UInt64.valueOf(12)));
    assertThat(removals).hasValue(3);
    assertThat(commits).hasValue(1);
    // Fewer distinct slots than the limit were available, so the limit was not the reason we
    // stopped
    assertThat(limitReached).isFalse();
  }

  @Test
  void pruneDataColumnSidecarsDoesNothingWhenNoSidecarsAtOrBeforeCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(10));

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(lookupSlots).containsExactly(UInt64.valueOf(30));
    assertThat(streamedRanges).isEmpty();
    assertThat(removals).hasValue(0);
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  @Test
  void pruneDataColumnSidecarsDoesNothingWhenFirstSupportedSlotIsAfterCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(40), 50);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(lookupSlots).isEmpty();
    assertThat(streamedRanges).isEmpty();
    assertThat(removals).hasValue(0);
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  private KvStoreDatabase database(final UInt64 firstFuluSlot, final long... populatedSlots) {
    final NavigableSet<UInt64> slots = new TreeSet<>();
    for (final long slot : populatedSlots) {
      slots.add(UInt64.valueOf(slot));
    }
    final Spec spec = mock(Spec.class);
    when(spec.computeFirstSlotWithDataColumnSidecarSupport())
        .thenReturn(Optional.of(firstFuluSlot));
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    doAnswer(
            (final InvocationOnMock invocation) -> {
              final UInt64 requested = invocation.getArgument(0);
              lookupSlots.add(requested);
              return Optional.ofNullable(slots.floor(requested));
            })
        .when(dao)
        .getPreviousDataColumnSidecarSlotAtOrBefore(any());
    return new KvStoreDatabase(dao, StateStorageMode.PRUNE, false, spec) {

      @Override
      protected FinalizedUpdater finalizedUpdater() {
        return countingUpdater(removals, commits);
      }

      @Override
      public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
          final UInt64 firstSlot, final UInt64 lastSlot) {
        streamedRanges.add(new SlotRange(firstSlot, lastSlot));
        return slots.subSet(firstSlot, true, lastSlot, true).stream()
            .map(slot -> identifier(slot.longValue(), 0));
      }
    };
  }

  private static FinalizedUpdater countingUpdater(
      final AtomicInteger removals, final AtomicInteger commits) {
    final FinalizedUpdater updater = mock(FinalizedUpdater.class);
    doAnswer(
            (final InvocationOnMock __) -> {
              removals.incrementAndGet();
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
