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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ForkProvider {
  private static final Logger LOG = LogManager.getLogger();

  private AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;

  private volatile Optional<ForkInfo> currentFork = Optional.empty();

  public ForkProvider(
      final AsyncRunner asyncRunner, final ValidatorApiChannel validatorApiChannel) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
  }

  public SafeFuture<ForkInfo> getForkInfo() {
    return currentFork.map(SafeFuture::completedFuture).orElseGet(this::loadForkInfo);
  }

  public SafeFuture<ForkInfo> loadForkInfo() {
    return requestForkInfo()
        .exceptionallyCompose(
            error -> {
              LOG.error("Failed to retrieve current fork info. Retrying after delay", error);
              return asyncRunner.runAfterDelay(
                  this::getForkInfo, FORK_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            });
  }

  public SafeFuture<ForkInfo> requestForkInfo() {
    return validatorApiChannel
        .getForkInfo()
        .thenCompose(
            maybeForkInfo -> {
              if (maybeForkInfo.isEmpty()) {
                LOG.trace("Fork info not available, retrying");
                return asyncRunner.runAfterDelay(
                    this::requestForkInfo, FORK_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
              }
              final ForkInfo forkInfo = maybeForkInfo.orElseThrow();
              currentFork = Optional.of(forkInfo);
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
}
