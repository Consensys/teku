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
import org.apache.tuweni.bytes.Bytes32;
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

  private volatile SafeFuture<ForkInfo> currentFork = new SafeFuture<>();

  public ForkProvider(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.genesisDataProvider = genesisDataProvider;
  }

  public SafeFuture<ForkInfo> getForkInfo() {
    return currentFork;
  }

  public SafeFuture<ForkInfo> loadForkInfo() {
    return requestForkInfo()
        .exceptionallyCompose(
            error -> {
              LOG.error("Failed to retrieve current fork info. Retrying after delay", error);
              return asyncRunner.runAfterDelay(
                  this::loadForkInfo, FORK_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            });
  }

  private SafeFuture<ForkInfo> requestForkInfo() {
    return genesisDataProvider.getGenesisValidatorsRoot().thenCompose(this::requestFork);
  }

  public SafeFuture<ForkInfo> requestFork(final Bytes32 genesisValidatorsRoot) {
    return validatorApiChannel
        .getFork()
        .thenCompose(
            maybeFork -> {
              if (maybeFork.isEmpty()) {
                LOG.trace("Fork info not available, retrying");
                return asyncRunner.runAfterDelay(
                    this::requestForkInfo, FORK_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
              }
              final ForkInfo forkInfo =
                  new ForkInfo(maybeFork.orElseThrow(), genesisValidatorsRoot);
              currentFork.complete(forkInfo);
              currentFork = SafeFuture.completedFuture(forkInfo);
              // Periodically refresh the current fork info.
              asyncRunner
                  .runAfterDelay(this::loadForkInfo, FORK_REFRESH_TIME_SECONDS, TimeUnit.SECONDS)
                  .reportExceptions();
              return SafeFuture.completedFuture(forkInfo);
            });
  }

  @Override
  public String toString() {
    return "ForkProvider{" + "currentFork=" + currentFork + '}';
  }

  @Override
  protected SafeFuture<?> doStart() {
    loadForkInfo().propagateTo(currentFork);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
