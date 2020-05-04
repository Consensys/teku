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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class RetryingDutyLoaderTest {

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final DutyLoader delegate = mock(DutyLoader.class);
  private final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);

  private final RetryingDutyLoader dutyLoader = new RetryingDutyLoader(asyncRunner, delegate);

  @Test
  public void shouldReturnDutiesWhenLoadedSuccessfully() {
    when(delegate.loadDutiesForEpoch(ONE)).thenReturn(SafeFuture.completedFuture(scheduledDuties));

    assertThat(dutyLoader.loadDutiesForEpoch(ONE)).isCompletedWithValue(scheduledDuties);
  }

  @Test
  public void shouldRetryWhenRequestForDutiesFailsBecauseNodeIsSyncing() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(NodeSyncingException.failedFuture())
        .thenReturn(SafeFuture.completedFuture(scheduledDuties));

    final SafeFuture<ScheduledDuties> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDuties);
  }

  @Test
  public void shouldRetryWhenRequestForDutiesFailsBecauseNodeDataIsUnavailable() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(SafeFuture.failedFuture(new NodeDataUnavailableException("Sorry")))
        .thenReturn(SafeFuture.completedFuture(scheduledDuties));

    final SafeFuture<ScheduledDuties> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDuties);
  }

  @Test
  public void shouldRetryWhenUnexpectedErrorOccurs() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("No way")))
        .thenReturn(SafeFuture.completedFuture(scheduledDuties));

    final SafeFuture<ScheduledDuties> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDuties);
  }

  @Test
  public void shouldStopRetryingWhenFutureIsCancelled() {
    final RuntimeException error = new RuntimeException("No way");
    final SafeFuture<ScheduledDuties> delegateResponse = new SafeFuture<>();
    when(delegate.loadDutiesForEpoch(ONE)).thenReturn(delegateResponse);

    final SafeFuture<ScheduledDuties> result = dutyLoader.loadDutiesForEpoch(ONE);
    verify(delegate).loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();

    result.cancel(false);

    // When the request then fails, we don't retry it.
    delegateResponse.completeExceptionally(error);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoMoreInteractions(delegate);
  }
}
