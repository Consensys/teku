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

package tech.pegasys.teku.validator.client.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class GenesisTimeProviderTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf(12341234);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final GenesisTimeProvider genesisTimeProvider =
      new GenesisTimeProvider(asyncRunner, validatorApiChannel);

  @Test
  void shouldRequestGenesisTimeWhenNotPreviouslyLoaded() {
    final SafeFuture<Optional<UInt64>> request = new SafeFuture<>();
    when(validatorApiChannel.getGenesisTime()).thenReturn(request);

    final SafeFuture<UInt64> result = genesisTimeProvider.getGenesisTime();
    assertThat(result).isNotDone();

    request.complete(Optional.of(GENESIS_TIME));
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }

  @Test
  void shouldReturnCachedGenesisTimeWhenPreviouslyLoaded() {
    when(validatorApiChannel.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(Optional.of(GENESIS_TIME)));
    assertThat(genesisTimeProvider.getGenesisTime()).isCompletedWithValue(GENESIS_TIME);
    verify(validatorApiChannel).getGenesisTime();

    // Subsequent requests just return the cached version
    assertThat(genesisTimeProvider.getGenesisTime()).isCompletedWithValue(GENESIS_TIME);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRetryWhenGenesisTimeFailsToLoad() {
    when(validatorApiChannel.getGenesisTime())
        .thenReturn(failedFuture(new RuntimeException("Nope")))
        .thenReturn(completedFuture(Optional.of(GENESIS_TIME)));

    // First request fails
    final SafeFuture<UInt64> result = genesisTimeProvider.getGenesisTime();
    verify(validatorApiChannel).getGenesisTime();
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getGenesisTime();
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }

  @Test
  void shouldRetryWhenGenesisTimeIsNotYetKnown() {
    when(validatorApiChannel.getGenesisTime())
        .thenReturn(completedFuture(Optional.empty()))
        .thenReturn(completedFuture(Optional.of(GENESIS_TIME)));

    // First request fails
    final SafeFuture<UInt64> result = genesisTimeProvider.getGenesisTime();
    verify(validatorApiChannel).getGenesisTime();
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getGenesisTime();
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }
}
