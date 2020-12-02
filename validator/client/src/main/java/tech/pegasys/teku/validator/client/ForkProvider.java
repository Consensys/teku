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

import static tech.pegasys.teku.util.config.Constants.FORK_REFRESH_TIME_SECONDS;
import static tech.pegasys.teku.util.config.Constants.FORK_RETRY_DELAY_SECONDS;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class ForkProvider extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final GenesisDataProvider genesisDataProvider;

  private volatile SafeFuture<ForkInfo> currentFork;

  public ForkProvider(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.genesisDataProvider = genesisDataProvider;
    currentFork = new SafeFuture<>();
  }

  public SafeFuture<ForkInfo> getForkInfo() {
    return currentFork;
  }

  private SafeFuture<ForkInfo> getForkAndPeriodicallyUpdate() {
    return validatorApiChannel
        .getFork()
        .thenCompose(
            maybeFork -> {
              if (maybeFork.isEmpty()) {
                LOG.trace("Fork info not available, retrying");
                return scheduleNextRequest(FORK_RETRY_DELAY_SECONDS);
              }

              scheduleNextRequest(FORK_REFRESH_TIME_SECONDS).reportExceptions();
              return getValidatorsRootAndCreateForkInfo(maybeFork.orElseThrow());
            })
        .exceptionallyCompose(
            error -> {
              LOG.error("Failed to retrieve current fork info. Retrying after delay", error);
              return scheduleNextRequest(FORK_RETRY_DELAY_SECONDS);
            });
  }

  private SafeFuture<ForkInfo> getValidatorsRootAndCreateForkInfo(Fork fork) {
    return genesisDataProvider
        .getGenesisValidatorsRoot()
        .thenCompose(
            root -> {
              final ForkInfo forkInfo = new ForkInfo(fork, root);
              // anything waiting on the current future needs to be notified
              currentFork.complete(forkInfo);
              currentFork = SafeFuture.completedFuture(forkInfo);
              return currentFork;
            });
  }

  private SafeFuture<ForkInfo> scheduleNextRequest(final long seconds) {
    return asyncRunner.runAfterDelay(this::getForkAndPeriodicallyUpdate, seconds, TimeUnit.SECONDS);
  }

  @Override
  public String toString() {
    return "ForkProvider{" + "currentFork=" + currentFork + '}';
  }

  @Override
  protected SafeFuture<?> doStart() {
    currentFork = getForkAndPeriodicallyUpdate();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
