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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class BeaconNodeReadinessManager extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final Map<RemoteValidatorApiChannel, Boolean> readinessStatusCache =
      Maps.newConcurrentMap();

  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final AsyncRunner asyncRunner;
  private final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel;
  private final Duration beaconNodeSyncingQueryPeriod;

  private volatile Cancellable syncingQueryTask;

  public BeaconNodeReadinessManager(
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final AsyncRunner asyncRunner,
      final RemoteBeaconNodeSyncingChannel remoteBeaconNodeSyncingChannel,
      final Duration beaconNodeSyncingQueryPeriod) {
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.asyncRunner = asyncRunner;
    this.remoteBeaconNodeSyncingChannel = remoteBeaconNodeSyncingChannel;
    this.beaconNodeSyncingQueryPeriod = beaconNodeSyncingQueryPeriod;
  }

  public boolean isReady(final RemoteValidatorApiChannel beaconNodeApi) {
    return readinessStatusCache.getOrDefault(beaconNodeApi, true);
  }

  @Override
  protected SafeFuture<?> doStart() {
    syncingQueryTask =
        asyncRunner.runWithFixedDelay(
            () -> {
              final SafeFuture<Void> primaryReadinessCheck = performPrimaryReadinessCheck();
              final Stream<SafeFuture<?>> failoverReadinessChecks =
                  failoverBeaconNodeApis.stream().map(this::performFailoverReadinessCheck);
              return SafeFuture.allOf(
                  primaryReadinessCheck, SafeFuture.allOf(failoverReadinessChecks));
            },
            beaconNodeSyncingQueryPeriod,
            beaconNodeSyncingQueryPeriod,
            throwable ->
                LOG.error(
                    "Error while querying the syncing status of the configured Beacon Nodes",
                    throwable));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(
        () -> {
          Optional.ofNullable(syncingQueryTask).ifPresent(Cancellable::cancel);
          readinessStatusCache.clear();
        });
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
              if (!syncingStatus.isSyncing() || syncingStatus.getIsOptimistic().orElse(false)) {
                LOG.debug("{} is synced and ready to accept requests", beaconNodeApiEndpoint);
                return true;
              }
              LOG.debug(
                  "{} is not ready to accept requests because it is not synced",
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
              final Optional<Boolean> maybeCachedReadiness =
                  Optional.ofNullable(readinessStatusCache.get(beaconNodeApi));
              readinessStatusCache.put(beaconNodeApi, isReady);
              if (isReady) {
                processReadyResult(isPrimaryNode, maybeCachedReadiness);
              } else {
                processNotReadyResult(beaconNodeApi, isPrimaryNode);
              }
            });
  }

  void processReadyResult(
      final boolean isPrimaryNode, final Optional<Boolean> maybeCachedReadiness) {
    if (!isPrimaryNode) {
      return;
    }
    maybeCachedReadiness.ifPresent(
        cachedReadiness -> {
          if (!cachedReadiness) {
            remoteBeaconNodeSyncingChannel.onPrimaryNodeBackInSync();
          }
        });
  }

  void processNotReadyResult(
      final RemoteValidatorApiChannel beaconNodeApi, final boolean isPrimaryNode) {
    if (isPrimaryNode) {
      remoteBeaconNodeSyncingChannel.onPrimaryNodeNotInSync();
    } else {
      remoteBeaconNodeSyncingChannel.onFailoverNodeNotInSync(beaconNodeApi);
    }
  }
}
