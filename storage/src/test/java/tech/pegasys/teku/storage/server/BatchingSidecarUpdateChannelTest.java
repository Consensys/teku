/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;

class BatchingSidecarUpdateChannelTest {
  private static final int BATCH_SIZE = 3;
  private static final Duration MAX_DELAY = Duration.ofMillis(50);

  private final SidecarUpdateChannel delegate = mock(SidecarUpdateChannel.class);
  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  private BatchingSidecarUpdateChannel channel;

  @BeforeEach
  void setUp() {
    channel = new BatchingSidecarUpdateChannel(delegate, stubAsyncRunner, BATCH_SIZE, MAX_DELAY);
    when(delegate.onNewSidecars(anyList())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldScheduleExecutionForFirstSidecar() {
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    channel.onNewSidecar(sidecar);

    assertExecutionScheduled();
    triggerBatchExecution(List.of(sidecar));
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldBatchMultipleSidecarsReceivedBeforeExecution() {
    final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);

    channel.onNewSidecar(sidecar1);
    assertExecutionScheduled();

    channel.onNewSidecar(sidecar2);

    triggerBatchExecution(List.of(sidecar1, sidecar2));
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldFlushImmediatelyWhenBatchSizeReached() {
    final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar3 = mock(DataColumnSidecar.class);

    // First sidecar - schedules execution
    channel.onNewSidecar(sidecar1);
    assertExecutionScheduled();

    // Second sidecar - still accumulating
    channel.onNewSidecar(sidecar2);

    // Third sidecar - hits batch size, should flush immediately
    channel.onNewSidecar(sidecar3);

    // Verify that the delegate was called immediately with all 3 sidecars
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DataColumnSidecar>> captor = ArgumentCaptor.forClass(List.class);
    verify(delegate).onNewSidecars(captor.capture());
    assertThat(captor.getValue()).containsExactly(sidecar1, sidecar2, sidecar3);
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored"})
  void shouldScheduleNewBatchAfterPreviousCompletes() {
    final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);

    channel.onNewSidecar(sidecar1);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    // Process first batch
    stubAsyncRunner.executeQueuedActions();
    verify(delegate, times(1)).onNewSidecars(List.of(sidecar1));

    // Add second sidecar, should schedule new execution
    channel.onNewSidecar(sidecar2);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    // Process second batch
    stubAsyncRunner.executeQueuedActions();
    verify(delegate, times(1)).onNewSidecars(List.of(sidecar2));
  }

  @Test
  void shouldNotCompleteFuturesUntilDelegateCompletes() {
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    final SafeFuture<Void> delegateFuture = new SafeFuture<>();
    when(delegate.onNewSidecars(anyList())).thenReturn(delegateFuture);

    final SafeFuture<Void> result = channel.onNewSidecar(sidecar);
    assertThat(result).isNotDone();

    // Trigger batch flush
    stubAsyncRunner.executeQueuedActions();

    // Caller future should still be pending - delegate hasn't completed yet
    assertThat(result).isNotDone();

    // Now complete the delegate
    delegateFuture.complete(null);

    // Caller future should now be completed
    assertThat(result).isCompleted();
  }

  @Test
  void shouldCompleteFuturesExceptionallyOnDelegateError() {
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    final RuntimeException error = new RuntimeException("Test error");
    when(delegate.onNewSidecars(anyList())).thenReturn(SafeFuture.failedFuture(error));

    final SafeFuture<Void> result = channel.onNewSidecar(sidecar);
    assertThat(result).isNotDone();

    stubAsyncRunner.executeQueuedActions();

    assertThat(result).isCompletedExceptionally();
    assertThat(result.exceptionNow()).isSameAs(error);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldDelegateOnFirstCustodyIncompleteSlotDirectly() {
    final UInt64 slot = UInt64.valueOf(100);
    when(delegate.onFirstCustodyIncompleteSlot(slot)).thenReturn(SafeFuture.COMPLETE);

    channel.onFirstCustodyIncompleteSlot(slot);

    verify(delegate).onFirstCustodyIncompleteSlot(slot);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldDelegateOnEarliestAvailableDataColumnSlotDirectly() {
    final UInt64 slot = UInt64.valueOf(100);
    when(delegate.onEarliestAvailableDataColumnSlot(slot)).thenReturn(SafeFuture.COMPLETE);

    channel.onEarliestAvailableDataColumnSlot(slot);

    verify(delegate).onEarliestAvailableDataColumnSlot(slot);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldDelegateOnNewSidecarsListDirectly() {
    final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);
    final List<DataColumnSidecar> sidecars = List.of(sidecar1, sidecar2);

    channel.onNewSidecars(sidecars);

    verify(delegate).onNewSidecars(sidecars);
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored", "unchecked"})
  void shouldFlushAfterDelayEvenWhenBatchNotFull() {
    final DataColumnSidecar sidecar1 = mock(DataColumnSidecar.class);
    final DataColumnSidecar sidecar2 = mock(DataColumnSidecar.class);

    // Add sidecars without reaching batch size
    channel.onNewSidecar(sidecar1);
    channel.onNewSidecar(sidecar2);

    // Should not flush immediately since batch size (3) not reached
    verify(delegate, never()).onNewSidecars(any());
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    // Simulate delay expiring - should trigger flush
    stubAsyncRunner.executeQueuedActions();

    // Verify batch was flushed with the 2 sidecars
    final ArgumentCaptor<List<DataColumnSidecar>> captor = ArgumentCaptor.forClass(List.class);
    verify(delegate).onNewSidecars(captor.capture());
    assertThat(captor.getValue()).containsExactly(sidecar1, sidecar2);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldRespectMaxDelayBeforeFlushing() {
    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final BatchingSidecarUpdateChannel channelWithTimeProvider =
        new BatchingSidecarUpdateChannel(delegate, asyncRunner, BATCH_SIZE, MAX_DELAY);

    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    channelWithTimeProvider.onNewSidecar(sidecar);

    // Advance time by less than MAX_DELAY (49ms < 50ms)
    timeProvider.advanceTimeByMillis(49);
    asyncRunner.executeDueActions();

    // Should NOT have flushed yet
    verify(delegate, never()).onNewSidecars(any());

    // Advance time to reach MAX_DELAY (now at 50ms)
    timeProvider.advanceTimeByMillis(1);
    asyncRunner.executeDueActions();

    // Should have flushed now
    verify(delegate).onNewSidecars(List.of(sidecar));
  }

  private void assertExecutionScheduled() {
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();
    verify(delegate, never()).onNewSidecars(any());
  }

  @SuppressWarnings("unchecked")
  private void triggerBatchExecution(final List<DataColumnSidecar> expectedBatch) {
    stubAsyncRunner.executeQueuedActions();
    ArgumentCaptor<List<DataColumnSidecar>> captor = ArgumentCaptor.forClass(List.class);
    verify(delegate, times(1)).onNewSidecars(captor.capture());
    assertThat(captor.getValue()).containsExactlyElementsOf(expectedBatch);
  }
}
