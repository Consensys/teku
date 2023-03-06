/*
 * Copyright ConsenSys Software Inc., 2023
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

public class EarliestAvailableBlockSlotTest {
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);
  private final EarliestAvailableBlockSlot earliestAvailableBlockSlot =
      new EarliestAvailableBlockSlot(historicalChainData, timeProvider, 10);

  @Test
  void shouldFetchFromChainDataOnFirstAccess() {
    when(historicalChainData.getEarliestAvailableBlockSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThat(earliestAvailableBlockSlot.get()).isCompleted();
    verify(historicalChainData).getEarliestAvailableBlockSlot();
  }

  @Test
  void shouldNotFetchOnSecondAccessIfTimeNotProgressed() {
    when(historicalChainData.getEarliestAvailableBlockSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThat(earliestAvailableBlockSlot.get()).isCompleted();
    assertThat(earliestAvailableBlockSlot.get()).isCompleted();
    verify(historicalChainData, times(1)).getEarliestAvailableBlockSlot();
  }

  @Test
  void shouldFetchOnSecondAccessIfOldQueryIsOld() {
    when(historicalChainData.getEarliestAvailableBlockSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThat(earliestAvailableBlockSlot.get()).isCompleted();
    timeProvider.advanceTimeBySeconds(10);
    assertThat(earliestAvailableBlockSlot.get()).isCompleted();
    verify(historicalChainData, times(2)).getEarliestAvailableBlockSlot();
  }

  @Test
  void shouldReuseInFlightQueryFuture() {
    final SafeFuture<Optional<UInt64>> future = new SafeFuture<>();
    when(historicalChainData.getEarliestAvailableBlockSlot()).thenReturn(future);

    final SafeFuture<Optional<UInt64>> first = earliestAvailableBlockSlot.get();
    assertThat(first).isNotCompleted();
    final SafeFuture<Optional<UInt64>> second = earliestAvailableBlockSlot.get();
    assertThat(second).isNotCompleted();
    assertThat(first).isEqualTo(second);

    final Optional<UInt64> maybeOne = Optional.of(UInt64.ONE);
    future.complete(maybeOne);
    assertThat(first).isCompletedWithValue(maybeOne);
    assertThat(second).isCompletedWithValue(maybeOne);
  }
}
