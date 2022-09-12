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

  private final Map<RemoteValidatorApiChannel, Boolean> readinessCache = Maps.newConcurrentMap();

  private final List<RemoteValidatorApiChannel> beaconNodeApis;
  private final AsyncRunner asyncRunner;
  private final Duration beaconNodeSyncingStatusQueryPeriod;

  private volatile Cancellable readinessTask;

  public BeaconNodeReadinessManager(
      final List<RemoteValidatorApiChannel> beaconNodeApis,
      final AsyncRunner asyncRunner,
      final Duration beaconNodeSyncingStatusQueryPeriod) {
    this.beaconNodeApis = beaconNodeApis;
    this.asyncRunner = asyncRunner;
    this.beaconNodeSyncingStatusQueryPeriod = beaconNodeSyncingStatusQueryPeriod;
  }

  public boolean isReady(final RemoteValidatorApiChannel beaconNodeApi) {
    return readinessCache.getOrDefault(beaconNodeApi, true);
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (beaconNodeApis.size() == 1) {
      LOG.debug("Not required to query the syncing status when only one Beacon Node is defined");
      return SafeFuture.COMPLETE;
    }
    readinessTask =
        asyncRunner.runWithFixedDelay(
            () -> {
              final Stream<SafeFuture<?>> readinessChecks =
                  beaconNodeApis.stream().map(this::performReadinessCheck);
              return SafeFuture.allOf(readinessChecks);
            },
            beaconNodeSyncingStatusQueryPeriod,
            beaconNodeSyncingStatusQueryPeriod,
            throwable ->
                LOG.error(
                    "Error while checking the readiness of the configured Beacon Nodes",
                    throwable));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(
        () -> {
          Optional.ofNullable(readinessTask).ifPresent(Cancellable::cancel);
          readinessCache.clear();
        });
  }

  private SafeFuture<Void> performReadinessCheck(final RemoteValidatorApiChannel beaconNodeApi) {
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
        .thenAccept(isReady -> readinessCache.put(beaconNodeApi, isReady));
  }
}
