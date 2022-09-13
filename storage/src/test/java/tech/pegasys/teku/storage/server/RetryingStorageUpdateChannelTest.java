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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

class RetryingStorageUpdateChannelTest {
  private final StorageUpdateChannel delegate = mock(StorageUpdateChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final RetryingStorageUpdateChannel retryingChannel =
      new RetryingStorageUpdateChannel(delegate, timeProvider);

  @Test
  void onStorageUpdate_shouldRetryUntilSuccess() {
    final StorageUpdate event = mock(StorageUpdate.class);
    when(delegate.onStorageUpdate(event))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final SafeFuture<UpdateResult> result = retryingChannel.onStorageUpdate(event);

    assertThat(result).isCompletedWithValue(UpdateResult.EMPTY);
    verify(delegate, times(3)).onStorageUpdate(event);
  }

  @Test
  void onFinalizedBlocks_shouldRetryUntilSuccess() {
    final List<SignedBeaconBlock> blocks = Collections.emptyList();
    when(delegate.onFinalizedBlocks(blocks))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<Void> result = retryingChannel.onFinalizedBlocks(blocks);

    assertThat(result).isCompleted();
    verify(delegate, times(3)).onFinalizedBlocks(blocks);
  }

  @Test
  void onFinalizedState_shouldRetryUntilSuccess() {
    final BeaconState state = mock(BeaconState.class);
    when(delegate.onFinalizedState(state, Bytes32.ZERO))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<Void> result = retryingChannel.onFinalizedState(state, Bytes32.ZERO);

    assertThat(result).isCompleted();
    verify(delegate, times(3)).onFinalizedState(state, Bytes32.ZERO);
  }

  @Test
  void onWeakSubjectivityUpdate_shouldRetryUntilSuccess() {
    final WeakSubjectivityUpdate update = mock(WeakSubjectivityUpdate.class);
    when(delegate.onWeakSubjectivityUpdate(update))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<Void> result = retryingChannel.onWeakSubjectivityUpdate(update);

    assertThat(result).isCompleted();
    verify(delegate, times(3)).onWeakSubjectivityUpdate(update);
  }

  @Test
  void onChainInitialized_shouldRetryUntilSuccess() {
    final AnchorPoint update = mock(AnchorPoint.class);
    final AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              final int count = callCount.incrementAndGet();
              if (count < 3) {
                throw new RuntimeException("Failed");
              }
              return null;
            })
        .when(delegate)
        .onChainInitialized(update);

    retryingChannel.onChainInitialized(update);

    verify(delegate, times(3)).onChainInitialized(update);
  }

  @Test
  void shouldThrowFatalExceptionWhenUpdateFailsForTooLong() {
    final StorageUpdate event = mock(StorageUpdate.class);
    when(delegate.onStorageUpdate(event))
        .thenAnswer(
            invocation -> {
              // Increase time a bit on each call
              timeProvider.advanceTimeBySeconds(10);
              return SafeFuture.failedFuture(new RuntimeException("Failed"));
            });

    assertThatThrownBy(() -> retryingChannel.onStorageUpdate(event))
        .isInstanceOf(FatalServiceFailureException.class);
    verify(delegate, times(7)).onStorageUpdate(event);
  }
}
