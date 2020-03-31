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

package tech.pegasys.artemis.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.async.SafeFuture.failedFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;

class ForkProviderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final Fork fork = dataStructureUtil.randomFork();

  private final ForkProvider forkProvider =
      new ForkProvider(asyncRunner, validatorApiChannel, Integer.MAX_VALUE, Integer.MAX_VALUE);

  @Test
  public void shouldRequestForkWhenNotPreviouslyLoaded() {
    final SafeFuture<Optional<Fork>> forkRequest = new SafeFuture<>();
    when(validatorApiChannel.getFork()).thenReturn(forkRequest);

    final SafeFuture<Fork> result = forkProvider.getFork();

    assertThat(result).isNotDone();

    forkRequest.complete(Optional.of(fork));
    assertThat(result).isCompletedWithValue(fork);
  }

  @Test
  public void shouldReturnCachedForkWhenPreviouslyLoaded() {
    when(validatorApiChannel.getFork()).thenReturn(completedFuture(Optional.of(fork)));

    // First request loads the fork
    assertThat(forkProvider.getFork()).isCompletedWithValue(fork);
    verify(validatorApiChannel).getFork();

    // Subsequent requests just return the cached version
    assertThat(forkProvider.getFork()).isCompletedWithValue(fork);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldPeriodicallyRequestUpdatedFork() {
    final Fork updatedFork = dataStructureUtil.randomFork();
    when(validatorApiChannel.getFork())
        .thenReturn(completedFuture(Optional.of(fork)))
        .thenReturn(completedFuture(Optional.of(updatedFork)));

    assertThat(forkProvider.getFork()).isCompletedWithValue(fork);

    // Update is scheduled
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // And after it runs we get the updated fork
    asyncRunner.executeQueuedActions();
    assertThat(forkProvider.getFork()).isCompletedWithValue(updatedFork);
  }

  @Test
  public void shouldRetryWhenForkFailsToLoad() {
    when(validatorApiChannel.getFork())
        .thenReturn(failedFuture(new RuntimeException("Nope")))
        .thenReturn(completedFuture(Optional.of(fork)));

    // First request fails
    final SafeFuture<Fork> result = forkProvider.getFork();
    verify(validatorApiChannel).getFork();
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getFork();
    assertThat(result).isCompletedWithValue(fork);
  }
}
