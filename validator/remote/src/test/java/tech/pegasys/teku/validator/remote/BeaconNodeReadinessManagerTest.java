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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.required.SyncingStatus;

class BeaconNodeReadinessManagerTest {

  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  private static final SyncingStatus SYNCED_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, false, Optional.empty());

  private static final SyncingStatus SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.empty());

  private static final SyncingStatus OPTIMISTICALLY_SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.of(true));

  private final RemoteValidatorApiChannel beaconNodeApi = mock(RemoteValidatorApiChannel.class);
  private final RemoteValidatorApiChannel failoverBeaconNodeApi =
      mock(RemoteValidatorApiChannel.class);

  private final Duration beaconNodeSyncingStatusQueryPeriod = Duration.ofMillis(100);

  private final BeaconNodeReadinessManager beaconNodeReadinessManager =
      new BeaconNodeReadinessManager(
          List.of(beaconNodeApi, failoverBeaconNodeApi),
          stubAsyncRunner,
          beaconNodeSyncingStatusQueryPeriod);

  @BeforeEach
  public void setUp() {
    beaconNodeReadinessManager.doStart();
  }

  @AfterEach
  public void cleanUp() {
    beaconNodeReadinessManager.doStop();
  }

  @Test
  public void retrievesReadinessOfBeaconNodes() {
    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCED_STATUS));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    // default to true if never ran
    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isTrue();

    stubTimeProvider.advanceTimeBy(beaconNodeSyncingStatusQueryPeriod);
    stubAsyncRunner.executeDueActions();

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(OPTIMISTICALLY_SYNCING_STATUS));

    stubTimeProvider.advanceTimeBy(beaconNodeSyncingStatusQueryPeriod);
    stubAsyncRunner.executeDueActions();

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isFalse();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isTrue();
  }

  @Test
  public void doesNotQuerySyncingStatusIfOnlyOneBeaconNodeIsDefined() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();
    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            List.of(beaconNodeApi), stubAsyncRunner, beaconNodeSyncingStatusQueryPeriod);

    beaconNodeReadinessManager.doStart();

    assertThat(stubAsyncRunner.countDelayedActions()).isZero();
    // default to true always if only one endpoint
    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();

    beaconNodeReadinessManager.doStop();
  }
}
