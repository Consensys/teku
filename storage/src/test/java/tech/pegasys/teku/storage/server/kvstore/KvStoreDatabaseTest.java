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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;

class KvStoreDatabaseTest {

  @Test
  void pruneDataColumnSidecarsStreamsOnlySlotsBeingPruned() {
    final AtomicInteger removals = new AtomicInteger();
    final List<UInt64> streamedSlots = new ArrayList<>();
    final List<UInt64> lookupStartSlots = new ArrayList<>();
    final Queue<Optional<UInt64>> nextSlots =
        new ArrayDeque<>(
            List.of(
                Optional.of(UInt64.ONE),
                Optional.of(UInt64.valueOf(2)),
                Optional.of(UInt64.valueOf(3))));
    final KvStoreDatabase database =
        databaseWithUpdater(removals, streamedSlots, lookupStartSlots, nextSlots);

    final boolean limitReached =
        database.pruneDataColumnSidecars(2, UInt64.MAX_VALUE, false, "canonical");

    assertThat(limitReached).isTrue();
    assertThat(lookupStartSlots).containsExactly(UInt64.ZERO, UInt64.valueOf(2));
    assertThat(streamedSlots).containsExactly(UInt64.ONE, UInt64.valueOf(2));
    assertThat(removals).hasValue(2);
  }

  private static KvStoreDatabase databaseWithUpdater(
      final AtomicInteger removals,
      final List<UInt64> streamedSlots,
      final List<UInt64> lookupStartSlots,
      final Queue<Optional<UInt64>> nextSlots) {
    return new KvStoreDatabase(
        mock(KvStoreCombinedDao.class), StateStorageMode.PRUNE, false, mock(Spec.class)) {

      @Override
      protected FinalizedUpdater finalizedUpdater() {
        return updaterCountingRemovals(removals);
      }

      @Override
      public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
          final UInt64 firstSlot, final UInt64 lastSlot) {
        if (!firstSlot.equals(lastSlot)) {
          lookupStartSlots.add(firstSlot);
          final Optional<UInt64> nextSlot =
              nextSlots.isEmpty() ? Optional.empty() : nextSlots.remove();
          return nextSlot
              .<Stream<DataColumnSlotAndIdentifier>>map(
                  slot -> Stream.of(identifier(slot.longValue(), 0)))
              .orElseGet(Stream::empty);
        }
        streamedSlots.add(firstSlot);
        return Stream.of(identifier(firstSlot.longValue(), 0));
      }
    };
  }

  private static FinalizedUpdater updaterCountingRemovals(final AtomicInteger removals) {
    final FinalizedUpdater updater = mock(FinalizedUpdater.class);
    doAnswer(
            __ -> {
              removals.incrementAndGet();
              return null;
            })
        .when(updater)
        .removeSidecar(any());

    return updater;
  }

  private static DataColumnSlotAndIdentifier identifier(final long slot, final long columnIndex) {
    return new DataColumnSlotAndIdentifier(
        UInt64.valueOf(slot), Bytes32.ZERO, UInt64.valueOf(columnIndex));
  }
}
