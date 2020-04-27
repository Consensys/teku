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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.util.async.SafeFuture.failedFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class ForkProviderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private final ForkProvider forkProvider = new ForkProvider(asyncRunner, validatorApiChannel);

  @Test
  public void shouldRequestForkWhenNotPreviouslyLoaded() {
    final SafeFuture<Optional<ForkInfo>> forkRequest = new SafeFuture<>();
    when(validatorApiChannel.getForkInfo()).thenReturn(forkRequest);

    final SafeFuture<ForkInfo> result = forkProvider.getForkInfo();

    assertThat(result).isNotDone();

    forkRequest.complete(Optional.of(fork));
    assertThat(result).isCompletedWithValue(fork);
  }

  @Test
  public void shouldReturnCachedForkWhenPreviouslyLoaded() {
    when(validatorApiChannel.getForkInfo()).thenReturn(completedFuture(Optional.of(fork)));

    // First request loads the fork
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(fork);
    verify(validatorApiChannel).getForkInfo();

    // Subsequent requests just return the cached version
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(fork);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldPeriodicallyRequestUpdatedFork() {
    final ForkInfo updatedFork = dataStructureUtil.randomForkInfo();
    when(validatorApiChannel.getForkInfo())
        .thenReturn(completedFuture(Optional.of(fork)))
        .thenReturn(completedFuture(Optional.of(updatedFork)));

    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(fork);

    // Update is scheduled
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // And after it runs we get the updated fork
    asyncRunner.executeQueuedActions();
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(updatedFork);
  }

  @Test
  public void shouldRetryWhenForkFailsToLoad() {
    when(validatorApiChannel.getForkInfo())
        .thenReturn(failedFuture(new RuntimeException("Nope")))
        .thenReturn(completedFuture(Optional.of(fork)));

    // First request fails
    final SafeFuture<ForkInfo> result = forkProvider.getForkInfo();
    verify(validatorApiChannel).getForkInfo();
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getForkInfo();
    assertThat(result).isCompletedWithValue(fork);
  }
}
