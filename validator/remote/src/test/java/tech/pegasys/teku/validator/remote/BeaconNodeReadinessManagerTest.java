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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.required.SyncingStatus;

public class BeaconNodeReadinessManagerTest {

  private static final SyncingStatus SYNCED_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, false, Optional.empty());

  private static final SyncingStatus SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.empty());

  private static final SyncingStatus OPTIMISTICALLY_SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, false, Optional.of(true));

  private final RemoteValidatorApiChannel beaconNodeApi = mock(RemoteValidatorApiChannel.class);
  private final RemoteValidatorApiChannel failoverBeaconNodeApi =
      mock(RemoteValidatorApiChannel.class);

  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel =
      mock(RemoteBeaconNodeSyncingChannel.class);

  private final BeaconNodeReadinessManager beaconNodeReadinessManager =
      new BeaconNodeReadinessManager(
          beaconNodeApi,
          List.of(failoverBeaconNodeApi),
          validatorLogger,
          remoteBeaconNodeSyncingChannel);

  @Test
  public void retrievesReadinessAndPublishesToAChannel() {
    // default to true if never ran
    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isTrue();

    verifyNoInteractions(validatorLogger, remoteBeaconNodeSyncingChannel);

    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCED_STATUS));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    verifyNoInteractions(validatorLogger);

    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(OPTIMISTICALLY_SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isFalse();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    verify(validatorLogger).primaryBeaconNodeNotReady(true);
    verify(remoteBeaconNodeSyncingChannel).onPrimaryNodeNotInSync();

    verify(remoteBeaconNodeSyncingChannel, times(2)).onFailoverNodeNotInSync(failoverBeaconNodeApi);

    // primary node recovers
    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCED_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();

    verify(validatorLogger).primaryBeaconNodeIsBackAndReady(true);
    verify(remoteBeaconNodeSyncingChannel).onPrimaryNodeBackInSync();
  }

  @Test
  public void ordersFailoversByReadiness() {
    final RemoteValidatorApiChannel anotherFailover = mock(RemoteValidatorApiChannel.class);
    final RemoteValidatorApiChannel yetAnotherFailover = mock(RemoteValidatorApiChannel.class);
    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            beaconNodeApi,
            List.of(failoverBeaconNodeApi, anotherFailover, yetAnotherFailover),
            validatorLogger,
            remoteBeaconNodeSyncingChannel);

    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCED_STATUS));

    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));
    when(anotherFailover.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCED_STATUS));
    when(yetAnotherFailover.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.getFailoversInOrderOfReadiness())
        .toIterable()
        .containsExactly(anotherFailover, failoverBeaconNodeApi, yetAnotherFailover);
  }

  private void advanceToNextQueryPeriod(
      final BeaconNodeReadinessManager beaconNodeReadinessManager) {
    beaconNodeReadinessManager.onAttestationAggregationDue(UInt64.ZERO);
  }
}
