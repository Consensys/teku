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
  void pruneDataColumnSidecarsStopsCollectingAfterPruneSlotLimit() {
    final AtomicInteger inspectedIdentifiers = new AtomicInteger();
    final AtomicInteger removals = new AtomicInteger();
    final KvStoreDatabase database = databaseWithUpdater(removals);

    final boolean limitReached =
        database.pruneDataColumnSidecars(
            2,
            Stream.concat(
                    Stream.of(identifier(1, 0), identifier(2, 0), identifier(3, 0)),
                    Stream.generate(() -> identifier(4, 0)).limit(1000))
                .peek(__ -> inspectedIdentifiers.incrementAndGet()),
            false);

    assertThat(limitReached).isTrue();
    assertThat(inspectedIdentifiers).hasValue(3);
    assertThat(removals).hasValue(2);
  }

  private static KvStoreDatabase databaseWithUpdater(final AtomicInteger removals) {
    return new KvStoreDatabase(
        mock(KvStoreCombinedDao.class), StateStorageMode.PRUNE, false, mock(Spec.class)) {

      @Override
      protected FinalizedUpdater finalizedUpdater() {
        return updaterCountingRemovals(removals);
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
