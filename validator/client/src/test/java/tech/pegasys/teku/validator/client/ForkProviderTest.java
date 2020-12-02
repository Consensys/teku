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
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

class ForkProviderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);

  private final ForkProvider forkProvider =
      new ForkProvider(asyncRunner, validatorApiChannel, genesisDataProvider);

  @Test
  public void shouldRequestForkWhenNotPreviouslyLoaded() {
    final SafeFuture<Optional<Fork>> forkRequest = new SafeFuture<>();
    when(validatorApiChannel.getFork()).thenReturn(forkRequest);
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(forkInfo.getGenesisValidatorsRoot()));

    forkProvider.start().reportExceptions();
    final SafeFuture<ForkInfo> result = forkProvider.getForkInfo();

    assertThat(result).isNotDone();

    forkRequest.complete(Optional.of(forkInfo.getFork()));
    assertThat(result).isCompletedWithValue(forkInfo);
  }

  @Test
  public void shouldReturnCachedForkWhenPreviouslyLoaded() {
    when(validatorApiChannel.getFork())
        .thenReturn(completedFuture(Optional.of(forkInfo.getFork())));
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(forkInfo.getGenesisValidatorsRoot()));

    // First request loads the fork
    forkProvider.start().reportExceptions();
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(forkInfo);
    verify(validatorApiChannel).getFork();

    // Subsequent requests just return the cached version
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(forkInfo);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldPeriodicallyRequestUpdatedFork() {
    final ForkInfo updatedFork =
        new ForkInfo(dataStructureUtil.randomFork(), forkInfo.getGenesisValidatorsRoot());
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(forkInfo.getGenesisValidatorsRoot()));
    when(validatorApiChannel.getFork())
        .thenReturn(completedFuture(Optional.of(forkInfo.getFork())))
        .thenReturn(completedFuture(Optional.of(updatedFork.getFork())));

    forkProvider.start().reportExceptions();
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(forkInfo);

    // Update is scheduled
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // And after it runs we get the updated fork
    asyncRunner.executeQueuedActions();
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(updatedFork);
  }

  @Test
  public void shouldOnlySendSingleRequestWhenForkNotPreviouslyLoaded() {
    final SafeFuture<Optional<Fork>> forkFuture = new SafeFuture<>();
    when(validatorApiChannel.getFork()).thenReturn(forkFuture);
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(forkInfo.getGenesisValidatorsRoot()));

    forkProvider.start().reportExceptions();
    // First request loads the fork
    final SafeFuture<ForkInfo> result1 = forkProvider.getForkInfo();
    final SafeFuture<ForkInfo> result2 = forkProvider.getForkInfo();
    assertThat(result1).isNotCompleted();
    assertThat(result2).isNotCompleted();
    verify(validatorApiChannel).getFork();
    verifyNoMoreInteractions(validatorApiChannel);

    forkFuture.complete(Optional.of(forkInfo.getFork()));
    assertThat(result1).isCompletedWithValue(this.forkInfo);
    assertThat(result2).isCompletedWithValue(this.forkInfo);

    // Subsequent requests just return the cached version
    assertThat(forkProvider.getForkInfo()).isCompletedWithValue(this.forkInfo);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRetryWhenForkFailsToLoad() {
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(forkInfo.getGenesisValidatorsRoot()));
    when(validatorApiChannel.getFork())
        .thenReturn(failedFuture(new RuntimeException("Nope")))
        .thenReturn(completedFuture(Optional.of(forkInfo.getFork())));
    forkProvider.start().reportExceptions();
    // First request fails
    final SafeFuture<ForkInfo> result = forkProvider.getForkInfo();
    verify(validatorApiChannel).getFork();
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getFork();
    assertThat(result).isCompletedWithValue(forkInfo);
  }
}
