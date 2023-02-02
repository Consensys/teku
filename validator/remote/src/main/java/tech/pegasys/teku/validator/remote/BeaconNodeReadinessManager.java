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

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class BeaconNodeReadinessManager implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Map<RemoteValidatorApiChannel, Boolean> readinessStatusCache =
      Maps.newConcurrentMap();

  private final AtomicBoolean latestPrimaryNodeReadiness = new AtomicBoolean(true);

  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final ValidatorLogger validatorLogger;
  private final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel;

  public BeaconNodeReadinessManager(
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final ValidatorLogger validatorLogger,
      final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel) {
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.validatorLogger = validatorLogger;
    this.remoteBeaconNodeSyncingChannel = remoteBeaconNodeSyncingChannel;
  }

  public boolean isReady(final RemoteValidatorApiChannel beaconNodeApi) {
    return readinessStatusCache.getOrDefault(beaconNodeApi, true);
  }

  public Iterator<RemoteValidatorApiChannel> getFailoversInOrderOfReadiness() {
    return failoverBeaconNodeApis.stream()
        .sorted(Comparator.comparing(this::isReady).reversed())
        .iterator();
  }

  @Override
  public void onSlot(final UInt64 slot) {}

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    final SafeFuture<Void> primaryReadinessCheck = performPrimaryReadinessCheck();
    final Stream<SafeFuture<?>> failoverReadinessChecks =
        failoverBeaconNodeApis.stream().map(this::performFailoverReadinessCheck);
    SafeFuture.allOf(primaryReadinessCheck, SafeFuture.allOf(failoverReadinessChecks))
        .finish(
            throwable ->
                LOG.error(
                    "Error while querying the syncing status of the configured Beacon Nodes",
                    throwable));
  }

  private SafeFuture<Void> performPrimaryReadinessCheck() {
    return performReadinessCheck(primaryBeaconNodeApi, true);
  }

  private SafeFuture<Void> performFailoverReadinessCheck(final RemoteValidatorApiChannel failover) {
    return performReadinessCheck(failover, false);
  }

  private SafeFuture<Void> performReadinessCheck(
      final RemoteValidatorApiChannel beaconNodeApi, final boolean isPrimaryNode) {
    final HttpUrl beaconNodeApiEndpoint = beaconNodeApi.getEndpoint();
    return beaconNodeApi
        .getSyncingStatus()
        .thenApply(
            syncingStatus -> {
              if (!syncingStatus.isElOffline()
                  && (!syncingStatus.isSyncing()
                      || syncingStatus.getIsOptimistic().orElse(false))) {
                LOG.debug("{} is in sync and ready to accept requests", beaconNodeApiEndpoint);
                return true;
              }
              LOG.debug(
                  "{} is not ready to accept requests because it is not in sync",
                  beaconNodeApiEndpoint);
              return false;
            })
        .exceptionally(
            throwable -> {
              LOG.debug(
                  String.format(
                      "%s is not ready to accept requests because the syncing status request failed",
                      beaconNodeApiEndpoint),
                  throwable);
              return false;
            })
        .thenAccept(
            isReady -> {
              readinessStatusCache.put(beaconNodeApi, isReady);
              if (isReady) {
                processReadyResult(isPrimaryNode);
              } else {
                processNotReadyResult(beaconNodeApi, isPrimaryNode);
              }
            });
  }

  private void processReadyResult(final boolean isPrimaryNode) {
    if (!isPrimaryNode) {
      return;
    }
    if (latestPrimaryNodeReadiness.compareAndSet(false, true)) {
      validatorLogger.primaryBeaconNodeIsBackAndReady(failoversConfigured());
      remoteBeaconNodeSyncingChannel.onPrimaryNodeBackInSync();
    }
  }

  private void processNotReadyResult(
      final RemoteValidatorApiChannel beaconNodeApi, final boolean isPrimaryNode) {
    if (isPrimaryNode) {
      if (latestPrimaryNodeReadiness.compareAndSet(true, false)) {
        validatorLogger.primaryBeaconNodeNotReady(failoversConfigured());
      }
      remoteBeaconNodeSyncingChannel.onPrimaryNodeNotInSync();
    } else {
      remoteBeaconNodeSyncingChannel.onFailoverNodeNotInSync(beaconNodeApi);
    }
  }

  private boolean failoversConfigured() {
    return !failoverBeaconNodeApis.isEmpty();
  }
}
