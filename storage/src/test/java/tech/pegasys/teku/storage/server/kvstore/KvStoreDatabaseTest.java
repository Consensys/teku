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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  private final List<SlotRange> streamedReverseRanges = new ArrayList<>();
  private final AtomicInteger removals = new AtomicInteger();
  private final AtomicInteger commits = new AtomicInteger();

  // Both the reverse-scan path (RocksDB / in-memory) and the floor-lookup path (LevelDB) must prune
  // the same distinct slots and commit exactly once. These scenarios are run against both.

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void prunesNewestDistinctSlotsInASingleCommit(final boolean reverseStreamSupported) {
    final KvStoreDatabase database =
        database(UInt64.valueOf(10), reverseStreamSupported, 28, 29, 30);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removals).hasValue(2);
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void prunesDistinctSlotsAcrossGaps(final boolean reverseStreamSupported) {
    // Only slots 1 and 100 are populated; with a limit of 2 both must be pruned even though there
    // are no slots in between them.
    final KvStoreDatabase database = database(UInt64.ZERO, reverseStreamSupported, 1, 100);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(100), DataColumnSidecarType.CANONICAL);

    assertThat(removals).hasValue(2);
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void stopsAtFirstSupportedSlot(final boolean reverseStreamSupported) {
    final KvStoreDatabase database =
        database(UInt64.valueOf(10), reverseStreamSupported, 10, 11, 12);

    final boolean limitReached =
        database.pruneDataColumnSidecars(10, UInt64.valueOf(12), DataColumnSidecarType.CANONICAL);

    assertThat(removals).hasValue(3);
    assertThat(commits).hasValue(1);
    // Fewer distinct slots than the limit were available, so the limit was not the reason we
    // stopped
    assertThat(limitReached).isFalse();
  }

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void prunesOnlyUpToTheLimitDistinctSlots(final boolean reverseStreamSupported) {
    final KvStoreDatabase database =
        database(UInt64.ZERO, reverseStreamSupported, 10, 11, 12, 13, 14);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);

    // Only the newest 2 slots (13 and 14) are pruned
    assertThat(removals).hasValue(2);
    assertThat(commits).hasValue(1);
    assertThat(limitReached).isTrue();
  }

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void doesNothingWhenNoSidecarsAtOrBeforeCutoff(final boolean reverseStreamSupported) {
    final KvStoreDatabase database = database(UInt64.valueOf(10), reverseStreamSupported);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removals).hasValue(0);
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  @ParameterizedTest(name = "reverseStreamSupported={0}")
  @ValueSource(booleans = {true, false})
  void doesNothingWhenFirstSupportedSlotIsAfterCutoff(final boolean reverseStreamSupported) {
    final KvStoreDatabase database = database(UInt64.valueOf(40), reverseStreamSupported, 50);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(lookupSlots).isEmpty();
    assertThat(streamedRanges).isEmpty();
    assertThat(streamedReverseRanges).isEmpty();
    assertThat(removals).hasValue(0);
    assertThat(commits).hasValue(0);
    assertThat(limitReached).isFalse();
  }

  @Test
  void reversePathUsesASingleReverseScanAndNoFloorLookups() {
    final KvStoreDatabase database = database(UInt64.ZERO, true, 28, 29, 30);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    // The reverse path collects everything from one backward scan over [first, cutoff]
    assertThat(streamedReverseRanges)
        .containsExactly(new SlotRange(UInt64.ZERO, UInt64.valueOf(30)));
    assertThat(lookupSlots).isEmpty();
    assertThat(streamedRanges).isEmpty();
  }

  @Test
  void floorPathUsesFloorLookupsAndASingleForwardRangeScan() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), false, 28, 29, 30);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(lookupSlots).containsExactly(UInt64.valueOf(30), UInt64.valueOf(29));
    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(29), UInt64.valueOf(30)));
    assertThat(streamedReverseRanges).isEmpty();
  }

  private KvStoreDatabase database(
      final UInt64 firstFuluSlot,
      final boolean reverseStreamSupported,
      final long... populatedSlots) {
    final NavigableSet<UInt64> slots = new TreeSet<>();
    for (final long slot : populatedSlots) {
      slots.add(UInt64.valueOf(slot));
    }
    final Spec spec = mock(Spec.class);
    when(spec.computeFirstSlotWithDataColumnSidecarSupport())
        .thenReturn(Optional.of(firstFuluSlot));
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    when(dao.isReverseStreamSupported()).thenReturn(reverseStreamSupported);
    doAnswer(
            (final InvocationOnMock invocation) -> {
              final UInt64 requested = invocation.getArgument(0);
              lookupSlots.add(requested);
              return Optional.ofNullable(slots.floor(requested));
            })
        .when(dao)
        .getPreviousDataColumnSidecarSlotAtOrBefore(any());
    doAnswer(
            (final InvocationOnMock invocation) -> {
              final UInt64 startSlot = invocation.getArgument(0);
              final UInt64 endSlot = invocation.getArgument(1);
              streamedReverseRanges.add(new SlotRange(startSlot, endSlot));
              return slots.subSet(startSlot, true, endSlot, true).descendingSet().stream()
                  .map(slot -> identifier(slot.longValue(), 0));
            })
        .when(dao)
        .streamDataColumnIdentifiersReverse(any(), any());
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
